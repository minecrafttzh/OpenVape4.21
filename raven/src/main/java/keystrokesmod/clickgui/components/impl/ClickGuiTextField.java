package keystrokesmod.clickgui.components.impl;

import keystrokesmod.module.impl.client.Gui;
import keystrokesmod.utility.RenderUtils;
import keystrokesmod.utility.font.RavenFontRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiTextField;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.lwjgl.opengl.GL11;

import java.lang.reflect.Field;

public class ClickGuiTextField {
    private static final float DEFAULT_TEXT_SCALE = 0.5f;

    private static final int BACKGROUND_COLOR = 0xFF222222;
    private static final int FOCUSED_BACKGROUND_COLOR = 0xFF2A2A2A;
    private static final int OUTLINE_COLOR = 0xFF3A3A3A;
    private static final int FOCUSED_OUTLINE_COLOR = 0xFF555500;
    private static final int SELECTION_COLOR = 0x804A90E2;
    private static final int CURSOR_COLOR = 0xFFD0D0D0;

    private static final Field LINE_SCROLL_OFFSET_FIELD = ReflectionHelper.findField(
        GuiTextField.class,
        "lineScrollOffset",
        "field_146225_q"
    );
    private static final Field CURSOR_POSITION_FIELD = ReflectionHelper.findField(
        GuiTextField.class,
        "cursorPosition",
        "field_146224_r"
    );
    private static final Field SELECTION_END_FIELD = ReflectionHelper.findField(
        GuiTextField.class,
        "selectionEnd",
        "field_146223_s"
    );
    private static final Field ENABLED_COLOR_FIELD = ReflectionHelper.findField(
        GuiTextField.class,
        "enabledColor",
        "field_146222_t"
    );
    private static final Field DISABLED_COLOR_FIELD = ReflectionHelper.findField(
        GuiTextField.class,
        "disabledColor",
        "field_146221_u"
    );
    private static final Field IS_ENABLED_FIELD = ReflectionHelper.findField(
        GuiTextField.class,
        "isEnabled",
        "field_146226_p"
    );

    private static int nextId;

    private final GuiTextField textField;
    private final String placeholder;
    private final float textScale;

    private long lastCursorTick;
    private boolean cursorVisible;

    public ClickGuiTextField(String placeholder, int maxLength) {
        this(placeholder, maxLength, DEFAULT_TEXT_SCALE);
    }

    public ClickGuiTextField(String placeholder, int maxLength, float textScale) {
        this.placeholder = placeholder == null ? "" : placeholder;
        this.textScale = textScale;
        this.textField = new GuiTextField(
            nextId++,
            Minecraft.getMinecraft().fontRendererObj,
            0,
            0,
            100,
            20
        );
        this.textField.setMaxStringLength(maxLength);
        this.textField.setEnableBackgroundDrawing(false);
        this.textField.setCanLoseFocus(true);
    }

    public void render(float left, float top, float right, float bottom) {
        float textLeft = left + 2.0f;
        float width = Math.max(0.0f, right - left - 4.0f);
        float backgroundBottom = bottom - 1.0f;
        float height = Math.max(0.0f, backgroundBottom - top);
        RavenFontRenderer renderer = Gui.getClickGuiSettingFontRenderer();

        String fullText = textField.getText();
        int lineScrollOffset = Math.max(0, Math.min(getIntField(textField, LINE_SCROLL_OFFSET_FIELD, 0), fullText.length()));
        String visibleText = getVisibleText(fullText, lineScrollOffset, width, renderer);
        int cursorPosition = getIntField(textField, CURSOR_POSITION_FIELD, fullText.length());
        int selectionEnd = getIntField(textField, SELECTION_END_FIELD, cursorPosition);
        int visibleStart = lineScrollOffset;
        int visibleEnd = visibleStart + visibleText.length();
        float textY = centeredScaledTextY(top, height, renderer);
        int textColor = getBooleanField(textField, IS_ENABLED_FIELD, true)
            ? getIntField(textField, ENABLED_COLOR_FIELD, 0xE0E0E0)
            : getIntField(textField, DISABLED_COLOR_FIELD, 0x707070);

        RenderUtils.drawRect(left, top, right, backgroundBottom, isFocused() ? FOCUSED_BACKGROUND_COLOR : BACKGROUND_COLOR);
        RenderUtils.drawOutline(left, top, right, backgroundBottom, 1.0f, isFocused() ? FOCUSED_OUTLINE_COLOR : OUTLINE_COLOR);

        if (!visibleText.isEmpty()) {
            drawSelection(textLeft, top, backgroundBottom, renderer, visibleText, cursorPosition, selectionEnd, visibleStart, visibleEnd);
            drawScaledText(visibleText, textLeft, textY, textColor, renderer);
        } else if (!textField.isFocused() && !placeholder.isEmpty()) {
            drawScaledText("\u00A77" + placeholder, textLeft, textY, 0xAAAAAA, renderer);
        }

        if (textField.isFocused() && cursorVisible) {
            drawCursor(textLeft, top, backgroundBottom, renderer, visibleText, cursorPosition, visibleStart, visibleEnd);
        }
    }

    public void tickCursor() {
        if (System.currentTimeMillis() - lastCursorTick >= 500L) {
            lastCursorTick = System.currentTimeMillis();
            cursorVisible = !cursorVisible;
        }
        textField.updateCursorCounter();
    }

    public boolean contains(int mouseX, int mouseY, float left, float top, float right, float bottom) {
        return mouseX >= left && mouseX <= right && mouseY >= top && mouseY <= bottom;
    }

    public boolean textboxKeyTyped(char typedChar, int keyCode) {
        return textField.textboxKeyTyped(typedChar, keyCode);
    }

    public String getText() {
        return textField.getText();
    }

    public void setText(String text) {
        textField.setText(text == null ? "" : text);
    }

    public boolean isFocused() {
        return textField.isFocused();
    }

    public void setFocused(boolean focused) {
        textField.setFocused(focused);
        if (focused) {
            cursorVisible = true;
            lastCursorTick = System.currentTimeMillis();
        }
    }

    private String getVisibleText(String text, int startIndex, float width, RavenFontRenderer renderer) {
        if (text == null || text.isEmpty() || width <= 0.0f) {
            return "";
        }

        float maxScaledWidth = width / textScale;
        String remaining = text.substring(startIndex);
        StringBuilder visible = new StringBuilder();

        for (int i = 0; i < remaining.length(); i++) {
            char character = remaining.charAt(i);
            String candidate = visible.toString() + character;
            if (renderer.getStringWidth(candidate) > maxScaledWidth) {
                break;
            }
            visible.append(character);
        }

        return visible.toString();
    }

    private void drawSelection(
        float textLeft,
        float top,
        float bottom,
        RavenFontRenderer renderer,
        String visibleText,
        int cursorPosition,
        int selectionEnd,
        int visibleStart,
        int visibleEnd
    ) {
        int selectionStart = Math.min(cursorPosition, selectionEnd);
        int selectionStop = Math.max(cursorPosition, selectionEnd);

        if (selectionStart == selectionStop || selectionStop <= visibleStart || selectionStart >= visibleEnd) {
            return;
        }

        int localStart = Math.max(0, selectionStart - visibleStart);
        int localEnd = Math.min(visibleText.length(), selectionStop - visibleStart);
        float selectionLeft = textLeft + renderer.getStringWidth(visibleText.substring(0, localStart)) * textScale;
        float selectionRight = textLeft + renderer.getStringWidth(visibleText.substring(0, localEnd)) * textScale;

        if (selectionRight > selectionLeft) {
            RenderUtils.drawRect(selectionLeft, top + 1.0f, selectionRight, bottom - 1.0f, SELECTION_COLOR);
        }
    }

    private void drawCursor(
        float textLeft,
        float top,
        float bottom,
        RavenFontRenderer renderer,
        String visibleText,
        int cursorPosition,
        int visibleStart,
        int visibleEnd
    ) {
        float cursorX;
        if (cursorPosition <= visibleStart) {
            cursorX = textLeft;
        } else if (cursorPosition >= visibleEnd) {
            cursorX = textLeft + renderer.getStringWidth(visibleText) * textScale;
        } else {
            cursorX = textLeft + renderer.getStringWidth(visibleText.substring(0, cursorPosition - visibleStart)) * textScale;
        }

        RenderUtils.drawRect(cursorX, top + 2.0f, cursorX + 1.0f, bottom - 2.0f, CURSOR_COLOR);
    }

    private float centeredScaledTextY(float top, float height, RavenFontRenderer renderer) {
        float textBoxHeight = Math.max(1.0f, (renderer.getTextBottomOffset() - renderer.getTextTopOffset()) * textScale);
        return top + (height - textBoxHeight) / 2.0f - renderer.getTextTopOffset() * textScale;
    }

    private void drawScaledText(String text, float x, float y, int color, RavenFontRenderer renderer) {
        GL11.glPushMatrix();
        GL11.glScaled(textScale, textScale, textScale);
        renderer.drawString(text, x / textScale, y / textScale, color, false);
        GL11.glPopMatrix();
    }

    private static int getIntField(GuiTextField textField, Field field, int fallback) {
        try {
            return field.getInt(textField);
        } catch (IllegalAccessException ignored) {
            return fallback;
        }
    }

    private static boolean getBooleanField(GuiTextField textField, Field field, boolean fallback) {
        try {
            return field.getBoolean(textField);
        } catch (IllegalAccessException ignored) {
            return fallback;
        }
    }
}
