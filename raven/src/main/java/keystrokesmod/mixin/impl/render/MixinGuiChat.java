package keystrokesmod.mixin.impl.render;

import keystrokesmod.Raven;
import keystrokesmod.clickgui.animation.ScrollOffsetAnimation;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiTextField;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import keystrokesmod.utility.RenderUtils;

@SideOnly(Side.CLIENT)
@Mixin(GuiChat.class)
public abstract class MixinGuiChat extends MixinGuiScreen {
    private static final int MAX_PREVIEW_ROWS = 6;
    private static final long PREVIEW_SCROLL_DURATION_MS = 200L;

    @Unique
    private String[] raven$commandCompletions = new String[0];

    @Unique
    private int raven$commandCompletionIndex = -1;

    @Unique
    private final ScrollOffsetAnimation raven$previewScrollAnim = new ScrollOffsetAnimation(PREVIEW_SCROLL_DURATION_MS);

    @Unique
    private String raven$previewScrollInput = "";

    @Shadow
    protected GuiTextField inputField;

    @Shadow
    private boolean waitingOnAutocomplete;

    @Shadow
    public abstract void onAutocompleteResponse(String[] suggestions);

    @Inject(method = "keyTyped", at = @At("RETURN"))
    private void updateLength(CallbackInfo callbackInfo) {
        if (Raven.commandManager != null && Raven.commandManager.isCommand(inputField.getText())) {
            inputField.setMaxStringLength(256);
        }
        else {
            inputField.setMaxStringLength(100);
        }
    }

    @Inject(method = "keyTyped", at = @At("HEAD"))
    private void clearCommandCompletionState(char typedChar, int keyCode, CallbackInfo callbackInfo) {
        if (keyCode != Keyboard.KEY_TAB) {
            raven$resetCommandCompletions();
        }
    }

    @Inject(method = "handleMouseInput", at = @At("HEAD"), cancellable = true)
    private void handleSuggestionScroll(CallbackInfo callbackInfo) {
        if (Raven.commandManager == null) {
            return;
        }

        int wheelInput = Mouse.getEventDWheel();
        if (wheelInput == 0) {
            return;
        }

        String input = inputField.getText();
        if (!Raven.commandManager.isCommand(input)) {
            raven$resetPreviewScroll();
            return;
        }

        String[] suggestions = Raven.commandManager.getPreviewSuggestions(input);
        int visibleRows = Math.min(MAX_PREVIEW_ROWS, suggestions.length);
        if (visibleRows == 0 || suggestions.length <= visibleRows) {
            raven$syncPreviewScrollState(input, suggestions.length, visibleRows);
            return;
        }

        int mouseX = Mouse.getEventX() * this.width / mc.displayWidth;
        int mouseY = this.height - Mouse.getEventY() * this.height / mc.displayHeight - 1;
        if (!raven$isMouseOverPreviewPanel(mouseX, mouseY, suggestions, visibleRows)) {
            return;
        }

        raven$syncPreviewScrollState(input, suggestions.length, visibleRows);
        float scrollSpeed = keystrokesmod.module.impl.client.Gui.scrollSpeed != null
            ? (float) keystrokesmod.module.impl.client.Gui.scrollSpeed.getInput()
            : 20f;
        float delta = scrollSpeed * (wheelInput / 120f);
        if (delta != 0f) {
            raven$previewScrollAnim.extend(-delta);
            raven$clampPreviewScroll(suggestions.length, visibleRows);
        }

        callbackInfo.cancel();
    }

    @Inject(method = "drawScreen", at = @At("RETURN"))
    private void drawLiveCommandSuggestions(int mouseX, int mouseY, float partialTicks, CallbackInfo callbackInfo) {
        if (Raven.commandManager == null) {
            raven$resetPreviewScroll();
            return;
        }

        String input = inputField.getText();
        if (!Raven.commandManager.isCommand(input)) {
            raven$resetPreviewScroll();
            return;
        }

        String[] suggestions = Raven.commandManager.getPreviewSuggestions(input);
        if (suggestions.length == 0) {
            raven$resetPreviewScroll();
            return;
        }

        int rows = Math.min(MAX_PREVIEW_ROWS, suggestions.length);
        raven$syncPreviewScrollState(input, suggestions.length, rows);
        int fontHeight = mc.fontRendererObj.FONT_HEIGHT;
        int rowHeight = fontHeight + 2;
        int widest = mc.fontRendererObj.getStringWidth("Suggestions");

        for (String suggestion : suggestions) {
            widest = Math.max(widest, mc.fontRendererObj.getStringWidth(suggestion));
        }

        int panelLeft = 4;
        int panelRight = Math.min(this.width - 4, panelLeft + widest + 10);
        int panelBottom = this.height - 16;
        int panelTop = panelBottom - 6 - fontHeight - rows * (fontHeight + 2);
        int contentTop = panelTop + 5 + fontHeight;
        int contentHeight = rows * rowHeight;

        Gui.drawRect(panelLeft, panelTop, panelRight, panelBottom, 0xC0101016);
        Gui.drawRect(panelLeft, panelTop, panelRight, panelTop + 1, 0xFFCC66FF);
        Gui.drawRect(panelLeft, panelBottom - 1, panelRight, panelBottom, 0x8020202A);

        mc.fontRendererObj.drawStringWithShadow("Suggestions", panelLeft + 4, panelTop + 3, 0xFFE8D8FF);

        RenderUtils.scissorPushGui(panelLeft, contentTop, panelRight - panelLeft, contentHeight);
        float scrollOffset = raven$previewScrollAnim.getValue();
        int activeIndex = raven$getActiveSuggestionIndex(input, suggestions);
        for (int i = 0; i < suggestions.length; i++) {
            int textY = Math.round(contentTop - scrollOffset + i * rowHeight);
            if (textY + fontHeight < contentTop || textY > contentTop + contentHeight) {
                continue;
            }

            int color = i == activeIndex ? 0xFFFFFFFF : 0xFFBFC4D6;
            mc.fontRendererObj.drawStringWithShadow(suggestions[i], panelLeft + 4, textY, color);
        }
        RenderUtils.scissorPop();
    }

    @Inject(method = "sendAutocompleteRequest", at = @At("HEAD"), cancellable = true)
    private void handleClientCommandCompletion(String full, String ignored, CallbackInfo callbackInfo) {
        if (Raven.commandManager == null || !Raven.commandManager.isCommand(full)) {
            return;
        }

        String[] suggestions = Raven.commandManager.getAutoComplete(full);
        if (suggestions.length == 0) {
            return;
        }

        if (suggestions.length == 1 && full.equalsIgnoreCase(suggestions[0])) {
            return;
        }

        waitingOnAutocomplete = true;
        this.onAutocompleteResponse(suggestions);
        callbackInfo.cancel();
    }

    @Inject(method = "autocompletePlayerNames", at = @At("HEAD"), cancellable = true)
    private void handleCommandAutocomplete(CallbackInfo callbackInfo) {
        if (Raven.commandManager == null) {
            return;
        }

        String input = inputField.getText();
        if (!Raven.commandManager.isCommand(input)) {
            raven$resetCommandCompletions();
            return;
        }

        String[] suggestions;
        if (raven$canCycleCurrentCompletion(input)) {
            suggestions = raven$commandCompletions;
            raven$commandCompletionIndex = (raven$commandCompletionIndex + 1) % suggestions.length;
        }
        else {
            suggestions = Raven.commandManager.getAutoComplete(input);
            if (suggestions.length == 0) {
                raven$resetCommandCompletions();
                callbackInfo.cancel();
                return;
            }

            raven$commandCompletions = suggestions;
            raven$commandCompletionIndex = 0;
        }

        inputField.setText(suggestions[raven$commandCompletionIndex]);
        inputField.setCursorPositionEnd();
        callbackInfo.cancel();
    }

    @Unique
    private boolean raven$canCycleCurrentCompletion(String input) {
        if (raven$commandCompletions.length == 0 || raven$commandCompletionIndex < 0) {
            return false;
        }

        for (String suggestion : raven$commandCompletions) {
            if (suggestion.equals(input)) {
                return true;
            }
        }

        return false;
    }

    @Unique
    private void raven$resetCommandCompletions() {
        raven$commandCompletions = new String[0];
        raven$commandCompletionIndex = -1;
    }

    @Unique
    private void raven$syncPreviewScrollState(String input, int suggestionCount, int visibleRows) {
        if (!input.equals(raven$previewScrollInput)) {
            raven$previewScrollInput = input;
            raven$previewScrollAnim.reset(0f);
        }
        raven$clampPreviewScroll(suggestionCount, visibleRows);
    }

    @Unique
    private void raven$clampPreviewScroll(int suggestionCount, int visibleRows) {
        int rowHeight = mc.fontRendererObj.FONT_HEIGHT + 2;
        float maxScroll = Math.max(0f, (suggestionCount - visibleRows) * rowHeight);
        raven$previewScrollAnim.clampTarget(0f, maxScroll);
    }

    @Unique
    private boolean raven$isMouseOverPreviewPanel(int mouseX, int mouseY, String[] suggestions, int visibleRows) {
        int fontHeight = mc.fontRendererObj.FONT_HEIGHT;
        int widest = mc.fontRendererObj.getStringWidth("Suggestions");
        for (String suggestion : suggestions) {
            widest = Math.max(widest, mc.fontRendererObj.getStringWidth(suggestion));
        }

        int panelLeft = 4;
        int panelRight = Math.min(this.width - 4, panelLeft + widest + 10);
        int panelBottom = this.height - 16;
        int panelTop = panelBottom - 6 - fontHeight - visibleRows * (fontHeight + 2);
        return mouseX >= panelLeft && mouseX <= panelRight && mouseY >= panelTop && mouseY <= panelBottom;
    }

    @Unique
    private int raven$getActiveSuggestionIndex(String input, String[] suggestions) {
        String loweredInput = input == null ? "" : input.toLowerCase();
        for (int i = 0; i < suggestions.length; i++) {
            if (loweredInput.endsWith(suggestions[i].toLowerCase())) {
                return i;
            }
        }
        return 0;
    }

    @Unique
    private void raven$resetPreviewScroll() {
        raven$previewScrollInput = "";
        raven$previewScrollAnim.reset(0f);
    }
}
