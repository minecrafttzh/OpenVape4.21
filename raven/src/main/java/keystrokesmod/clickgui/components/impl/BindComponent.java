package keystrokesmod.clickgui.components.impl;

import keystrokesmod.Raven;
import keystrokesmod.clickgui.components.Component;
import keystrokesmod.module.Module;
import keystrokesmod.module.impl.client.Gui;
import keystrokesmod.module.setting.impl.KeySetting;
import keystrokesmod.utility.RenderUtils;
import keystrokesmod.utility.Theme;
import keystrokesmod.utility.font.RavenFontRenderer;
import keystrokesmod.utility.profile.ProfileModule;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

public class BindComponent extends Component {
    private static final String EYE_ICON_PATH = "/assets/keystrokesmod/textures/gui/eye.png";
    private static final String EYE_OFF_ICON_PATH = "/assets/keystrokesmod/textures/gui/eye_off.png";
    private static final int EYE_ICON_PADDING = 2;

    public boolean isBinding;
    public ModuleComponent moduleComponent;
    public float o;
    public float x;
    private float y;
    public KeySetting keySetting;
    public float xOffset;

    public BindComponent(ModuleComponent moduleComponent, float o) {
        this.moduleComponent = moduleComponent;
        this.x = moduleComponent.categoryComponent.getX() + moduleComponent.categoryComponent.getWidth();
        this.y = moduleComponent.categoryComponent.getY() + moduleComponent.yPos;
        this.o = o;
    }

    public BindComponent(ModuleComponent moduleComponent, KeySetting keySetting, float o) {
        this.moduleComponent = moduleComponent;
        this.x = moduleComponent.categoryComponent.getX() + moduleComponent.categoryComponent.getWidth();
        this.y = moduleComponent.categoryComponent.getY() + moduleComponent.yPos;
        this.keySetting = keySetting;
        this.o = o;
    }

    public void updateHeight(float n) {
        this.o = n;
    }

    @Override public float getOffset() { return o; }
    @Override public boolean isBaseVisible() { return keySetting == null || keySetting.visible; }

    public void render() {
        RavenFontRenderer renderer = Gui.getClickGuiSettingFontRenderer();
        GL11.glPushMatrix();
        GL11.glScaled(0.5D, 0.5D, 0.5D);
        if (keySetting == null) {
            this.drawString(renderer, !this.moduleComponent.mod.canBeEnabled() && this.moduleComponent.mod.script == null ? "Module cannot be bound." : this.isBinding ? "Press a key..." : "Current bind: '\u00a7e" + getKeyAsStr(false) + "\u00a7r'");
        }
        else {
            renderer.drawString(this.isBinding ? "Press a key..." : this.keySetting.getName() + ": '\u00a7e" + getKeyAsStr(true) + "\u00a7r'", (float) ((this.moduleComponent.categoryComponent.getX() + 4) * 2) + xOffset, (float) ((this.moduleComponent.categoryComponent.getY() + this.o + (this.keySetting == null ? 3 : 4)) * 2), Theme.getGradient(Theme.descriptor[0], Theme.descriptor[1], 0), true);
        }
        GL11.glPopMatrix();

        if (keySetting == null && moduleComponent.mod.moduleCategory() != Module.category.profiles) {
            int iconSize = getEyeIconSize();
            float iconX = getEyeIconX(iconSize);
            float textHeight = Gui.getClickGuiSettingFontRenderer().getFontHeight() * 0.5f;
            float iconY = getRenderTextY() + (textHeight - iconSize) / 2f;

            int themeColor = !moduleComponent.mod.hidden
                    ? Theme.getGradient(Theme.descriptor[0], Theme.descriptor[1], 0)
                    : Theme.getGradient(Theme.hiddenBind[0], Theme.hiddenBind[1], 0);
            String iconPath = moduleComponent.mod.isHidden() ? EYE_OFF_ICON_PATH : EYE_ICON_PATH;
            RenderUtils.drawIcon(RenderUtils.getIcon(iconPath), iconX, iconY, iconSize, themeColor);
        }
    }

    public void drawScreen(int x, int y) {
        this.y = moduleComponent.categoryComponent.getModuleY() + o;
        this.x = moduleComponent.categoryComponent.getX();
    }

    public boolean onClick(int x, int y, int button) {
        if (!overSetting(x, y) || !moduleComponent.isOpened || !moduleComponent.isVisible(this)) return false;
        if (button == 0 && moduleComponent.mod.moduleCategory() != Module.category.profiles && overEyeIcon(x, y)) {
            moduleComponent.mod.setHidden(!moduleComponent.mod.isHidden());
            if (Raven.currentProfile != null) Raven.currentProfile.getModule().saved = false;
            return true;
        }
        if (moduleComponent.mod.canBeEnabled() && button == 0 && overBindText(x, y)) {
            isBinding = !isBinding;
            return true;
        }
        if (moduleComponent.mod.canBeEnabled() && button > 1 && isBinding) {
            if (keySetting != null) keySetting.setKey(button + 1000);
            else moduleComponent.mod.setBind(button + 1000);
            if (Raven.currentProfile != null) Raven.currentProfile.getModule().saved = false;
            isBinding = false;
            return true;
        }
        return false;
    }

    private boolean overEyeIcon(int x, int y) {
        int iconSize = getEyeIconSize();
        float iconX = getEyeIconX(iconSize);
        float iconY = getEyeIconY(iconSize);
        return x >= iconX && x < iconX + iconSize && y >= iconY && y < iconY + iconSize;
    }

    private float getBindTextX() {
        return moduleComponent.categoryComponent.getX() + 4f + (xOffset * 0.5f);
    }

    private float getBindTextY() {
        return moduleComponent.categoryComponent.getModuleY() + o + (keySetting == null ? 3f : 4f);
    }

    private float getRenderTextY() {
        return moduleComponent.categoryComponent.getY() + o + (keySetting == null ? 3f : 4f);
    }

    private String getBindDisplayString() {
        if (keySetting == null)
            return !moduleComponent.mod.canBeEnabled() && moduleComponent.mod.script == null ? "Module cannot be bound."
                    : isBinding ? "Press a key..." : "Current bind: '\u00a7e" + getKeyAsStr(false) + "\u00a7r'";
        return isBinding ? "Press a key..." : keySetting.getName() + ": '\u00a7e" + getKeyAsStr(true) + "\u00a7r'";
    }

    private boolean overBindText(int mouseX, int mouseY) {
        String text = getBindDisplayString();
        RavenFontRenderer renderer = Gui.getClickGuiSettingFontRenderer();

        float left = getBindTextX();
        float top = getBindTextY();
        float width = renderer.getStringWidth(text) * 0.5f;
        float height = renderer.getFontHeight() * 0.5f;

        return mouseX >= left && mouseX < left + width
                && mouseY >= top && mouseY < top + height;
    }

    private int getEyeIconSize() {
        int fontH = Math.round(Gui.getClickGuiSettingFontRenderer().getFontHeight() * 0.5f);
        return Math.max(6, fontH - 1);
    }

    private float getEyeIconX(int iconSize) {
        return moduleComponent.categoryComponent.getX() + moduleComponent.categoryComponent.getWidth() - iconSize - EYE_ICON_PADDING;
    }

    private float getEyeIconY(int iconSize) {
        float textY = getBindTextY();
        float textHeight = Gui.getClickGuiSettingFontRenderer().getFontHeight() * 0.5f;
        return textY + (textHeight - iconSize) / 2f;
    }

    public void onScroll(int scroll) {
        if (!isBinding || scroll == 0) return;
        if (keySetting != null) keySetting.setKey(scroll > 0 ? 1069 : 1070);
        else moduleComponent.mod.setBind(scroll > 0 ? 1069 : 1070);
        if (Raven.currentProfile != null) Raven.currentProfile.getModule().saved = false;
        isBinding = false;
    }

    public void keyTyped(char t, int keybind) {
        if (!isBinding) return;
        if (keybind == Keyboard.KEY_0 || keybind == Keyboard.KEY_ESCAPE) {
            if (moduleComponent.mod instanceof Gui) moduleComponent.mod.setBind(54);
            else if (keySetting != null) keySetting.setKey(0);
            else moduleComponent.mod.setBind(0);
        } else {
            if (keySetting != null) keySetting.setKey(keybind);
            else moduleComponent.mod.setBind(keybind);
        }
        if (Raven.currentProfile != null) Raven.currentProfile.getModule().saved = false;
        isBinding = false;
    }

    public boolean overSetting(int mouseX, int mouseY) {
        float rowX = moduleComponent.categoryComponent.getX();
        float rowY = moduleComponent.categoryComponent.getModuleY() + o;
        float rowW = moduleComponent.categoryComponent.getWidth();
        return mouseX > rowX && mouseX < rowX + rowW && mouseY > rowY - 1 && mouseY < rowY + 12;
    }

    public String getKeyAsStr(boolean isKey) {
        int key = isKey ? keySetting.getKey() : moduleComponent.mod.getKeycode();
        return key >= 1000 ? ((key == 1069 || key == 1070) ? getScroll(key) : "M" + (key - 1000)) : Keyboard.getKeyName(key);
    }

    public String getScroll(int key) {
        if (key == 1069) return "MScrollUp";
        if (key == 1070) return "MScrollDown";
        return "&cERROR";
    }

    @Override public float getHeightF() { return keySetting != null ? 0f : 16f; }
    @Override public int getHeight() { return Math.round(getHeightF()); }

    private void drawString(RavenFontRenderer renderer, String s) {
        renderer.drawString(s, (float) ((this.moduleComponent.categoryComponent.getX() + 4) * 2) + xOffset, (float) ((this.moduleComponent.categoryComponent.getY() + this.o + (this.keySetting == null ? 3 : 4)) * 2), Theme.getGradient(Theme.descriptor[0], Theme.descriptor[1], 0), true);
    }

    public void onGuiClosed() { isBinding = false; }

}
