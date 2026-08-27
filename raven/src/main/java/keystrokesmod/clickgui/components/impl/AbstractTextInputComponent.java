package keystrokesmod.clickgui.components.impl;

import keystrokesmod.clickgui.components.Component;
import keystrokesmod.clickgui.components.FocusableTextComponent;
import keystrokesmod.utility.Theme;
import keystrokesmod.utility.font.RavenFontRenderer;
import org.lwjgl.opengl.GL11;

public abstract class AbstractTextInputComponent extends Component implements FocusableTextComponent {
    protected static final float ROW_HEIGHT = 12f;
    protected static final float TEXT_SCALE = 0.5f;
    protected static final int DEFAULT_TEXT_MAX_LENGTH = 128;

    protected final ModuleComponent moduleComponent;
    public float o;
    public float xOffset;

    private final ClickGuiTextField textField;

    protected static final class Layout {
        float cx;
        float cy;
        float cw;
        float left;
        float right;
        float searchTop;
        float contentTop;
    }

    protected AbstractTextInputComponent(ModuleComponent moduleComponent, float o, String placeholder, int maxLength) {
        this.moduleComponent = moduleComponent;
        this.o = o;
        this.textField = new ClickGuiTextField(placeholder, maxLength, TEXT_SCALE);
    }

    protected final ClickGuiTextField getTextField() {
        return textField;
    }

    protected final Layout layout(boolean useModuleY) {
        Layout layout = new Layout();
        layout.cx = moduleComponent.categoryComponent.getX();
        layout.cy = useModuleY ? moduleComponent.categoryComponent.getModuleY() : moduleComponent.categoryComponent.getY();
        layout.cw = moduleComponent.categoryComponent.getWidth();
        layout.left = layout.cx + 4f + (xOffset / 2f);
        layout.right = layout.cx + layout.cw - 4f;
        layout.searchTop = layout.cy + o + ROW_HEIGHT;
        layout.contentTop = layout.cy + o + (2f * ROW_HEIGHT);
        return layout;
    }

    protected final void renderTextInput(String label) {
        Layout layout = layout(false);
        renderLabel(layout, label);
        renderTextField(layout);
    }

    protected final void renderLabel(Layout layout, String label) {
        drawScaledText(
            label,
            layout.left,
            centeredScaledTextY(layout.cy + o, ROW_HEIGHT),
            Theme.getGradient(Theme.descriptor[0], Theme.descriptor[1], 0)
        );
    }

    protected final void renderTextField(Layout layout) {
        float boxTop = layout.searchTop + 1f;
        float boxBottom = layout.searchTop + ROW_HEIGHT - 1f;
        textField.render(layout.left, boxTop, layout.right, boxBottom);
    }

    protected final boolean isTextFieldClicked(int mouseX, int mouseY, Layout layout) {
        float boxTop = layout.searchTop + 1f;
        float boxBottom = layout.searchTop + ROW_HEIGHT - 1f;
        return textField.contains(mouseX, mouseY, layout.left, boxTop, layout.right, boxBottom);
    }

    protected final float getTextFieldTop(Layout layout) {
        return layout.searchTop + 1f;
    }

    protected final float getTextFieldBottom(Layout layout) {
        return layout.searchTop + ROW_HEIGHT - 1f;
    }

    protected final void setTextFieldFocused(boolean focused) {
        textField.setFocused(focused);
    }

    protected final boolean isTextFieldFocused() {
        return textField.isFocused();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY) {
        textField.tickCursor();
    }

    @Override
    public void updateHeight(float n) {
        this.o = n;
    }

    @Override
    public float getOffset() {
        return o;
    }

    @Override
    public float getHeightF() {
        return ROW_HEIGHT * 2f;
    }

    @Override
    public boolean isTextInputFocused() {
        return isTextFieldFocused();
    }

    @Override
    public boolean containsClick(int mouseX, int mouseY) {
        return isTextFieldClicked(mouseX, mouseY, layout(true));
    }

    @Override
    public void unfocusTextInput() {
        setTextFieldFocused(false);
    }

    @Override
    public void onGuiClosed() {
        setTextFieldFocused(false);
    }

    public void setXOffset(float xOffset) {
        this.xOffset = xOffset;
    }

    public abstract String getGroupName();

    protected static float centeredScaledTextY(float top, float height) {
        return centeredScaledTextY(top, height, TEXT_SCALE);
    }

    protected static float centeredScaledTextY(float top, float height, float scale) {
        RavenFontRenderer renderer = keystrokesmod.module.impl.client.Gui.getClickGuiSettingFontRenderer();
        float textBoxHeight = Math.max(1f, (renderer.getTextBottomOffset() - renderer.getTextTopOffset()) * scale);
        return top + (height - textBoxHeight) / 2f - renderer.getTextTopOffset() * scale;
    }

    protected static void drawScaledText(String text, float x, float y, int color) {
        drawScaledText(text, x, y, color, TEXT_SCALE);
    }

    protected static void drawScaledText(String text, float x, float y, int color, float scale) {
        RavenFontRenderer renderer = keystrokesmod.module.impl.client.Gui.getClickGuiSettingFontRenderer();
        GL11.glPushMatrix();
        GL11.glScaled(scale, scale, scale);
        renderer.drawString(text, x / scale, y / scale, color, false);
        GL11.glPopMatrix();
    }
}
