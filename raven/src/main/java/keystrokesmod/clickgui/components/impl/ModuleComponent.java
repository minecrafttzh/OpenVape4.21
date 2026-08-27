package keystrokesmod.clickgui.components.impl;

import keystrokesmod.Raven;
import keystrokesmod.clickgui.ClickGui;
import keystrokesmod.clickgui.components.Component;
import keystrokesmod.module.Module;
import keystrokesmod.module.setting.Setting;
import keystrokesmod.module.setting.impl.*;
import keystrokesmod.module.impl.client.Gui;
import keystrokesmod.utility.RenderUtils;
import keystrokesmod.utility.Timer;
import keystrokesmod.utility.Utils;
import keystrokesmod.utility.font.RavenFontRenderer;
import keystrokesmod.utility.profile.Manager;
import keystrokesmod.utility.profile.ProfileModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.nio.IntBuffer;
import java.util.Map;

public class ModuleComponent extends Component {
    public Module mod;
    public CategoryComponent categoryComponent;
    public float yPos;
    public ArrayList<Component> settings;
    public boolean isOpened;
    private boolean hovering;
    private Timer hoverTimer;
    private boolean hoverStarted;
    private Timer smoothTimer;
    private float smoothingY = 16f;
    private float animationStartY = 16f;
    private float animationTargetY = 16f;

    private static final IntBuffer SCISSOR_BOX = BufferUtils.createIntBuffer(16);
    /** Fixed x indent for settings under groups (visual only, not animated). */
    private static final float GROUP_CHILD_INDENT = 6f;

    private static final int ORIGINAL_HOVER_ALPHA = 120;

    private static final int HOVER_COLOR = new Color(0, 0, 0, ORIGINAL_HOVER_ALPHA).getRGB();
    private static final int UNSAVED_COLOR = new Color(114, 188, 250).getRGB();
    private static final int INVALID_COLOR = new Color(255, 80, 80).getRGB();
    private static final int ENABLED_COLOR = new Color(24, 154, 255).getRGB();
    private static final int DISABLED_COLOR = new Color(192, 192, 192).getRGB();
    private final boolean categoryManager;
    private final Map<Component, GroupComponent> owningGroups = new IdentityHashMap<Component, GroupComponent>();
    private final Map<String, GroupComponent> groupsByName = new HashMap<String, GroupComponent>();

    public ModuleComponent(Module mod, CategoryComponent p, float yPos) {
        this.mod = mod;
        this.categoryComponent = p;
        this.yPos = yPos;
        this.settings = new ArrayList();
        this.categoryManager = mod instanceof Manager || mod instanceof keystrokesmod.script.Manager;
        this.isOpened = categoryManager;
        float collapsedHeight = getCollapsedHeight();
        this.smoothingY = collapsedHeight;
        this.animationStartY = collapsedHeight;
        this.animationTargetY = collapsedHeight;
        rebuildSettingsList();
    }

    private void rebuildSettingsList() {
        this.settings = new ArrayList();
        float y = yPos + getSettingStartOffset();
        if (mod != null && !mod.getSettings().isEmpty()) {
            for (Setting v : mod.getSettings()) {
                if (!v.visible) {
                    continue;
                }
                if (v instanceof SliderSetting) {
                    SliderSetting n = (SliderSetting) v;
                    SliderComponent s = new SliderComponent(n, this, y);
                    this.settings.add(s);
                    y += 12;
                }
                else if (v instanceof ButtonSetting) {
                    ButtonSetting b = (ButtonSetting) v;
                    ButtonComponent c = new ButtonComponent(mod, b, this, y);
                    this.settings.add(c);
                    y += 12;
                }
                else if (v instanceof DescriptionSetting) {
                    DescriptionSetting d = (DescriptionSetting) v;
                    DescriptionComponent m = new DescriptionComponent(d, this, y);
                    this.settings.add(m);
                    y += 12;
                }
                else if (v instanceof KeySetting) {
                    KeySetting setting = (KeySetting) v;
                    BindComponent keyComponent = new BindComponent(this, setting, y);
                    this.settings.add(keyComponent);
                    y += 12;
                }
                else if (v instanceof GroupSetting) {
                    GroupSetting b = (GroupSetting) v;
                    GroupComponent c = new GroupComponent(b, this, y);
                    this.settings.add(c);
                    y += 12;
                }
                else if (v instanceof ColorSetting) {
                    ColorSetting cs = (ColorSetting) v;
                    ColorComponent cc = new ColorComponent(cs, this, y);
                    this.settings.add(cc);
                    y += 12;
                }
                else if (v instanceof PotionListSetting) {
                    PotionListSetting pls = (PotionListSetting) v;
                    PotionSearchComponent psc = new PotionSearchComponent(pls, this, y);
                    this.settings.add(psc);
                    y += 12;
                }
                else if (v instanceof InventoryItemListSetting) {
                    InventoryItemListSetting iils = (InventoryItemListSetting) v;
                    InventoryItemSearchComponent iisc = new InventoryItemSearchComponent(iils, this, y);
                    this.settings.add(iisc);
                    y += 12;
                }
                else if (v instanceof ItemListSetting) {
                    ItemListSetting ils = (ItemListSetting) v;
                    ItemSearchComponent isc = new ItemSearchComponent(ils, this, y);
                    this.settings.add(isc);
                    y += 12;
                }
                else if (v instanceof PlayerListSetting) {
                    PlayerListSetting pls = (PlayerListSetting) v;
                    PlayerListComponent plc = new PlayerListComponent(pls, this, y);
                    this.settings.add(plc);
                    y += plc.getHeightF();
                }
                else if (v instanceof StringListSetting) {
                    StringListSetting sls = (StringListSetting) v;
                    StringListComponent slc = new StringListComponent(sls, this, y);
                    this.settings.add(slc);
                    y += slc.getHeightF();
                }
                else if (v instanceof keystrokesmod.module.setting.impl.BlockListSetting) {
                    keystrokesmod.module.setting.impl.BlockListSetting bls = (keystrokesmod.module.setting.impl.BlockListSetting) v;
                    BlockSearchComponent bsc = new BlockSearchComponent(bls, this, y);
                    this.settings.add(bsc);
                    y += 12;
                }
                else if (v instanceof TextSetting) {
                    TextSetting ts = (TextSetting) v;
                    TextFieldComponent tfc = new TextFieldComponent(ts, this, y);
                    this.settings.add(tfc);
                    y += tfc.getHeightF();
                }
            }
        }
        if (!categoryManager) {
            this.settings.add(new BindComponent(this, y));
        }
        rebuildGroupOwnershipCache();
    }

    public void reloadSettings() {
        boolean wasOpened = this.isOpened;
        Map<SliderSetting, Boolean> sliderHeldStates = new HashMap<SliderSetting, Boolean>();
        Map<ColorSetting, Boolean> colorExpandedStates = new HashMap<ColorSetting, Boolean>();

        for (Component component : this.settings) {
            if (component instanceof SliderComponent) {
                SliderComponent sliderComponent = (SliderComponent) component;
                sliderHeldStates.put(sliderComponent.sliderSetting, sliderComponent.heldDown);
            }
            else if (component instanceof ColorComponent) {
                ColorComponent colorComponent = (ColorComponent) component;
                colorExpandedStates.put(colorComponent.colorSetting, colorComponent.expanded);
            }
        }

        rebuildSettingsList();
        for (Component component : this.settings) {
            if (component instanceof SliderComponent) {
                SliderComponent sliderComponent = (SliderComponent) component;
                Boolean wasHeldDown = sliderHeldStates.get(sliderComponent.sliderSetting);
                if (wasHeldDown != null) {
                    sliderComponent.heldDown = wasHeldDown;
                }
            }
            else if (component instanceof ColorComponent) {
                ColorComponent colorComponent = (ColorComponent) component;
                Boolean wasExpanded = colorExpandedStates.get(colorComponent.colorSetting);
                if (wasExpanded != null) {
                    colorComponent.restoreExpandedState(wasExpanded);
                }
            }
        }
        restoreOpenState(wasOpened);
        updateSettingPositions();
    }

    public void restoreOpenState(boolean opened) {
        this.isOpened = categoryManager || opened;
        this.smoothTimer = null;
        float height = this.isOpened ? getHeightF() : getCollapsedHeight();
        this.smoothingY = height;
        this.animationStartY = height;
        this.animationTargetY = height;
    }

    public void updateAnimationState() {
        if (smoothTimer != null) {
            if (System.currentTimeMillis() - smoothTimer.last >= 280) {
                smoothTimer = null;
                smoothingY = animationTargetY;
                animationStartY = animationTargetY;
            } else {
                smoothingY = smoothTimer.getValueFloat(animationStartY, animationTargetY, 1);
                if (smoothingY == animationTargetY) {
                    smoothTimer = null;
                    animationStartY = animationTargetY;
                }
            }
        }
    }

    public void updateHeight(float newY) {
        this.yPos = newY;
        float y = this.yPos + getCollapsedHeight();
        int idx = 0;
        while (idx < this.settings.size()) {
            Component co = this.settings.get(idx);
            if (!isVisibleBase(co)) {
                idx++;
                continue;
            }

            if (co instanceof GroupComponent) {
                GroupComponent group = (GroupComponent) co;
                float progress = group.getAnimationProgress();

                co.updateHeight(y);
                float groupHeaderY = y;
                y += getBaseComponentHeightF(co);
                idx++;

                // Position children at FULL heights so the scissor reveals them top-to-bottom
                float childY = y;
                float totalChildrenFullHeight = 0f;
                while (idx < this.settings.size()) {
                    Component child = this.settings.get(idx);
                    if (!isVisibleBase(child)) { idx++; continue; }
                    if (getOwningGroup(child) != group) break;

                    child.updateHeight(childY);
                    float baseH = getBaseComponentHeightF(child);
                    childY += baseH;
                    totalChildrenFullHeight += baseH;

                    if (child instanceof SliderComponent) {
                        ((SliderComponent) child).xOffset = GROUP_CHILD_INDENT;
                    } else if (child instanceof ButtonComponent) {
                        ((ButtonComponent) child).xOffset = GROUP_CHILD_INDENT;
                    } else if (child instanceof BindComponent && ((BindComponent) child).keySetting != null) {
                        ((BindComponent) child).xOffset = GROUP_CHILD_INDENT;
                    } else if (child instanceof ColorComponent) {
                        ((ColorComponent) child).xOffset = GROUP_CHILD_INDENT;
                    } else if (child instanceof AbstractTextInputComponent) {
                        ((AbstractTextInputComponent) child).setXOffset(GROUP_CHILD_INDENT);
                    }
                    idx++;
                }

                // Items AFTER the group advance by the animated (collapsed) amount
                y = groupHeaderY + getBaseComponentHeightF(group) + totalChildrenFullHeight * progress;
            } else {
                co.updateHeight(y);

                GroupComponent group = getOwningGroup(co);
                float indent = (group != null) ? GROUP_CHILD_INDENT : 0f;
                if (co instanceof SliderComponent) {
                    ((SliderComponent) co).xOffset = indent;
                } else if (co instanceof ButtonComponent) {
                    ((ButtonComponent) co).xOffset = indent;
                } else if (co instanceof BindComponent && ((BindComponent) co).keySetting != null) {
                    ((BindComponent) co).xOffset = indent;
                } else if (co instanceof ColorComponent) {
                    ((ColorComponent) co).xOffset = indent;
                } else if (co instanceof AbstractTextInputComponent) {
                    ((AbstractTextInputComponent) co).setXOffset(indent);
                }

                y += getBaseComponentHeightF(co);
                idx++;
            }
        }
    }

    public void render() {
        if (hasModuleHeader() && (hovering || hoverTimer != null)) {
            double hoverAlpha = (hovering && hoverTimer != null) ? hoverTimer.getValueFloat(0, ORIGINAL_HOVER_ALPHA, 1) : (hoverTimer != null && !hovering) ? ORIGINAL_HOVER_ALPHA - hoverTimer.getValueFloat(0, ORIGINAL_HOVER_ALPHA, 1) : ORIGINAL_HOVER_ALPHA;
            if (hoverAlpha == 0) {
                hoverTimer = null;
            }
            RenderUtils.drawRoundedRectangle(this.categoryComponent.getX(), this.categoryComponent.getY() + yPos, this.categoryComponent.getX() + this.categoryComponent.getWidth(), this.categoryComponent.getY() + 16 + this.yPos, 8, Utils.mergeAlpha(HOVER_COLOR, (int) hoverAlpha));
        }
        int button_rgb = this.mod.isEnabled() ? ENABLED_COLOR : DISABLED_COLOR;
        if (this.mod.script != null && this.mod.script.error) {
            button_rgb = INVALID_COLOR;
        }
        if (this.mod.moduleCategory() == Module.category.profiles && !(this.mod instanceof Manager) && !((ProfileModule) this.mod).saved && Raven.currentProfile != null && Raven.currentProfile.getModule() == this.mod) {
            button_rgb = UNSAVED_COLOR;
        }

        boolean scissorRequired = smoothTimer != null;
        RavenFontRenderer titleRenderer = Gui.getClickGuiHeaderFontRenderer();

        if (hasModuleHeader()) {
            float textX = this.categoryComponent.getX() + this.categoryComponent.getWidth() / 2.0f - titleRenderer.getStringWidth(this.mod.getName()) / 2.0f;
            float textY = this.categoryComponent.getY() + this.yPos + 4;
            titleRenderer.drawString(this.mod.getName(), textX, textY, button_rgb, true);
        }
        if (scissorRequired) {
            ScaledResolution sr = new ScaledResolution(Minecraft.getMinecraft());
            int scale = sr.getScaleFactor();
            double guiScale = ClickGui.getActiveRenderScale();
            float scrollOffset = this.categoryComponent.getModuleY() - this.categoryComponent.getY();
            int scissorX = (int) Math.floor((this.categoryComponent.getX() - 2) * guiScale * scale);
            int scissorY = (int) Math.floor((sr.getScaledHeight() - ((this.categoryComponent.getY() + this.yPos + smoothingY + scrollOffset) * guiScale)) * scale);
            int scissorW = (int) Math.ceil((this.categoryComponent.getWidth() + 4) * guiScale * scale);
            int scissorH = (int) Math.ceil(smoothingY * guiScale * scale);
            pushScissor(scissorX, scissorY, scissorW, scissorH);
        }

        if (this.isOpened || smoothTimer != null) {
            renderSettingsWithGroupScissorReveal();
        }

        if (scissorRequired) {
            popScissor();
        }
    }

    /** Float height used for all layout/scroll decisions. */
    @Override
    public float getHeightF() {
        if (smoothTimer != null) {
            return smoothingY;
        }
        if (!this.isOpened) {
            return getCollapsedHeight();
        }
        float h = getCollapsedHeight();
        for (Component c : this.settings) {
            h += getAnimatedComponentHeightF(c);
        }
        return h;
    }

    /** Compat wrapper. */
    @Override
    public int getHeight() {
        return Math.round(getHeightF());
    }

    public void onSliderChange() {
        for (Component c : this.settings) {
            if (c instanceof SliderComponent) {
                ((SliderComponent) c).onSliderChange();
            }
        }
    }

    /**
     * Scroll-extent height: full target height when opening (so scroll bounds grow
     * immediately), current animated height when closing.
     */
    public float getScrollExtentHeightF() {
        if (isOpened || (smoothTimer != null && animationTargetY > 16f)) {
            float h = getCollapsedHeight();
            for (Component c : settings) {
                if (!isVisibleBase(c)) continue;
                GroupComponent group = getOwningGroup(c);
                float progress = group != null ? group.getAnimationProgress() : 1f;
                float effectiveProgress = (group != null && group.opened) ? Math.max(progress, 1f) : progress;
                h += getBaseComponentHeightF(c) * effectiveProgress;
            }
            return h;
        }
        return getHeightF();
    }

    public void drawScreen(int x, int y) {
        for (Component c : this.settings) {
            c.drawScreen(x, y);
        }
        if (hasModuleHeader() && overModuleName(x, y) && this.categoryComponent.opened) {
            hovering = true;
            if (hoverTimer == null) {
                (hoverTimer = new Timer(75)).start();
                hoverStarted = true;
            }
        }
        else {
            if (hovering && hoverStarted) {
                (hoverTimer = new Timer(75)).start();
            }
            hoverStarted = false;
            hovering = false;
        }
    }

    public boolean onClick(int x, int y, int mouse) {
        if (hasModuleHeader() && this.overModuleName(x, y) && mouse == 0 && this.mod.canBeEnabled()) {
            this.mod.toggle();
            if (this.mod.moduleCategory() != Module.category.profiles) {
                if (Raven.currentProfile != null) {
                    Raven.currentProfile.getModule().saved = false;
                }
            }
            return true;
        }

        if (hasModuleHeader() && this.overModuleName(x, y) && mouse == 1) {
            float currentHeight = smoothTimer != null ? smoothingY : (isOpened ? getHeightF() : 16f);
            this.animationStartY = currentHeight;
            this.isOpened = !this.isOpened;
            // Compute full open height without smoothTimer interference
            float targetHeight;
            if (this.isOpened) {
                float h = getCollapsedHeight();
                for (Component c : this.settings) {
                    h += getAnimatedComponentHeightF(c);
                }
                targetHeight = h;
            } else {
                targetHeight = getCollapsedHeight();
            }
            this.animationTargetY = targetHeight;
            (this.smoothTimer = new Timer(250)).start();
            return true;
        }

        for (Component settingComponent : this.settings) {
            if (settingComponent.onClick(x, y, mouse)) {
                return true;
            }
        }
        return false;
    }

    public void mouseReleased(int x, int y, int m) {
        for (Component c : this.settings) {
            c.mouseReleased(x, y, m);
        }
    }

    public void keyTyped(char t, int k) {
        for (Component c : this.settings) {
            c.keyTyped(t, k);
        }
    }

    public void onScroll(int scroll) {
        for (Component component : this.settings) {
            component.onScroll(scroll);
        }
    }

    public void onGuiClosed() {
        for (Component c : this.settings) {
            c.onGuiClosed();
        }
        smoothTimer = null;
        hoverTimer = null;
        float finalHeight = isOpened ? getHeightF() : getCollapsedHeight();
        smoothingY = finalHeight;
        animationStartY = finalHeight;
        animationTargetY = finalHeight;
    }

    public boolean overModuleName(int x, int y) {
        if (!hasModuleHeader()) {
            return false;
        }
        return x > this.categoryComponent.getX() && x < this.categoryComponent.getX() + this.categoryComponent.getWidth() && y > this.categoryComponent.getModuleY() + this.yPos && y < this.categoryComponent.getModuleY() + 16 + this.yPos;
    }

    public void updateSettingPositions() {
        this.categoryComponent.updateHeight();
    }

    public boolean isVisible(Component component) {
        if (!isVisibleBase(component)) {
            return false;
        }
        GroupComponent group = getOwningGroup(component);
        if (group == null) {
            return true;
        }
        return group.getAnimationProgress() > 0;
    }

    private GroupComponent getOwningGroup(Component component) {
        return owningGroups.get(component);
    }

    private String getGroupName(Component component) {
        if (component instanceof SliderComponent && ((SliderComponent) component).sliderSetting.groupSetting != null) {
            return ((SliderComponent) component).sliderSetting.groupSetting.getName();
        }
        if (component instanceof ButtonComponent && ((ButtonComponent) component).buttonSetting.group != null) {
            return ((ButtonComponent) component).buttonSetting.group.getName();
        }
        if (component instanceof BindComponent && ((BindComponent) component).keySetting != null && ((BindComponent) component).keySetting.group != null) {
            return ((BindComponent) component).keySetting.group.getName();
        }
        if (component instanceof ColorComponent && ((ColorComponent) component).colorSetting.groupSetting != null) {
            return ((ColorComponent) component).colorSetting.groupSetting.getName();
        }
        if (component instanceof AbstractTextInputComponent) {
            return ((AbstractTextInputComponent) component).getGroupName();
        }
        return "";
    }

    private void rebuildGroupOwnershipCache() {
        owningGroups.clear();
        groupsByName.clear();

        for (Component component : this.settings) {
            if (component instanceof GroupComponent) {
                GroupComponent groupComponent = (GroupComponent) component;
                groupsByName.put(groupComponent.setting.getName(), groupComponent);
            }
        }

        for (Component component : this.settings) {
            String groupName = getGroupName(component);
            if (!groupName.isEmpty()) {
                GroupComponent groupComponent = groupsByName.get(groupName);
                if (groupComponent != null) {
                    owningGroups.put(component, groupComponent);
                }
            }
        }
    }

    /** Base height for a component in pixels (float). */
    private float getBaseComponentHeightF(Component component) {
        if (component instanceof SliderComponent) {
            return 16f;
        }
        if (component instanceof ColorComponent) {
            ColorComponent cc = (ColorComponent) component;
            float progress = cc.getAnimationProgress();
            return 12f + (cc.getExpandedHeight() - 12f) * progress;
        }
        if (component instanceof AbstractSearchListComponent || component instanceof TextFieldComponent || component instanceof PlayerListComponent || component instanceof StringListComponent) {
            return component.getHeightF();
        }
        return 12f;
    }

    private float getAnimatedComponentHeightF(Component component) {
        if (!isVisibleBase(component)) {
            return 0f;
        }
        float base = getBaseComponentHeightF(component);
        GroupComponent group = getOwningGroup(component);
        float progress = group != null ? group.getAnimationProgress() : 1f;
        return base * progress;
    }

    /**
     * Renders settings with category-style scissor reveal for groups.
     * Children are laid out at full positions; one scissor per group grows from
     * the header downward, revealing children top-to-bottom (identical to
     * module expand/collapse).
     * Two-pass render: first headers and non-group items, then group children
     * (so closing animation is visible—children drawn on top).
     */
    private void renderSettingsWithGroupScissorReveal() {
        // Pass 1: render headers and non-group items
        int i = 0;
        while (i < settings.size()) {
            Component c = settings.get(i);
            if (!isVisibleBase(c)) { i++; continue; }
            if (c instanceof GroupComponent) {
                ((GroupComponent) c).render();
                i++;
                while (i < settings.size()) {
                    Component child = settings.get(i);
                    if (!isVisibleBase(child)) { i++; continue; }
                    if (getOwningGroup(child) != c) break;
                    i++;
                }
            } else {
                c.render();
                i++;
            }
        }
        // Pass 2: render group children with scissor (on top, so closing animation visible)
        i = 0;
        while (i < settings.size()) {
            Component c = settings.get(i);
            if (!isVisibleBase(c)) { i++; continue; }
            if (c instanceof GroupComponent) {
                GroupComponent group = (GroupComponent) c;
                i++;
                float progress = group.getAnimationProgress();
                float groupContentTop = this.categoryComponent.getModuleY()
                        + group.getOffset() + getBaseComponentHeightF(group);
                float groupContentHeight = 0f;
                int j = i;
                while (j < settings.size()) {
                    Component child = settings.get(j);
                    if (!isVisibleBase(child)) { j++; continue; }
                    if (getOwningGroup(child) != group) break;
                    groupContentHeight += getBaseComponentHeightF(child);
                    j++;
                }
                if (progress > 0f && groupContentHeight > 0f) {
                    float revealHeight = groupContentHeight * progress;
                    ScaledResolution sr = new ScaledResolution(Minecraft.getMinecraft());
                    int sf = sr.getScaleFactor();
                    double guiScale = ClickGui.getActiveRenderScale();
                    double screenH = sr.getScaledHeight();
                    float compLeft = this.categoryComponent.getX();
                    float compWidth = this.categoryComponent.getWidth() + 4;
                    int newLeft = (int) Math.floor(compLeft * guiScale * sf);
                    int newRight = (int) Math.ceil((compLeft + compWidth) * guiScale * sf);
                    int newW = Math.max(0, newRight - newLeft);
                    int newGlBottom = (int) Math.floor((screenH - ((groupContentTop + revealHeight) * guiScale)) * sf);
                    int newGlTop = (int) Math.ceil((screenH - (groupContentTop * guiScale)) * sf);
                    int newH = Math.max(0, newGlTop - newGlBottom);
                    pushScissor(newLeft, newGlBottom, newW, newH);
                    while (i < j) {
                        Component child = settings.get(i);
                        if (isVisibleBase(child) && getOwningGroup(child) == group) {
                            child.render();
                        }
                        i++;
                    }
                    popScissor();
                } else {
                    i = j;
                }
            } else {
                i++;
            }
        }
    }

    private static final int MAX_SCISSOR_DEPTH = 4;
    private final int[][] scissorStack = new int[MAX_SCISSOR_DEPTH][5];
    private int scissorDepth = 0;

    /**
     * Saves the current scissor state onto a stack, then applies a new scissor
     * rectangle intersected with the existing one if scissor was already enabled.
     * Supports nesting (module scissor + group scissor).
     */
    private void pushScissor(int x, int y, int w, int h) {
        boolean wasEnabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        int[] saved = scissorStack[scissorDepth++];
        if (wasEnabled) {
            SCISSOR_BOX.clear();
            GL11.glGetInteger(GL11.GL_SCISSOR_BOX, SCISSOR_BOX);
            saved[0] = 1;
            saved[1] = SCISSOR_BOX.get(0);
            saved[2] = SCISSOR_BOX.get(1);
            saved[3] = SCISSOR_BOX.get(2);
            saved[4] = SCISSOR_BOX.get(3);
            int ix = Math.max(saved[1], x);
            int iy = Math.max(saved[2], y);
            int iw = Math.max(0, Math.min(saved[1] + saved[3], x + w) - ix);
            int ih = Math.max(0, Math.min(saved[2] + saved[4], y + h) - iy);
            GL11.glScissor(ix, iy, iw, ih);
        } else {
            saved[0] = 0;
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            GL11.glScissor(x, y, w, h);
        }
    }

    private void popScissor() {
        int[] saved = scissorStack[--scissorDepth];
        if (saved[0] == 1) {
            GL11.glScissor(saved[1], saved[2], saved[3], saved[4]);
        } else {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
        }
    }

    private boolean isVisibleBase(Component component) {
        return component.isBaseVisible();
    }

    private boolean hasModuleHeader() {
        return !categoryManager;
    }

    private float getCollapsedHeight() {
        return hasModuleHeader() ? 16f : 0f;
    }

    private float getSettingStartOffset() {
        return hasModuleHeader() ? 12f : 0f;
    }
}
