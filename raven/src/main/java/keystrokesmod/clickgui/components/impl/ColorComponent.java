package keystrokesmod.clickgui.components.impl;

import keystrokesmod.Raven;
import keystrokesmod.clickgui.components.Component;
import keystrokesmod.module.impl.client.Gui;
import keystrokesmod.module.setting.impl.ColorSetting;
import keystrokesmod.utility.RenderUtils;
import keystrokesmod.utility.Timer;
import keystrokesmod.utility.font.RavenFontRenderer;
import org.lwjgl.opengl.GL11;

import java.awt.Color;

public class ColorComponent extends Component {
    public ColorSetting colorSetting;
    private ModuleComponent moduleComponent;
    public float o;
    public float x;
    private float y;
    public float xOffset;
    public boolean expanded;
    private int dragMode;
    private float cachedHue;
    private float cachedSat;
    private float cachedBri;

    private Timer smoothTimer;
    private float animationProgress;
    private float animationStartProgress;
    private float animationTargetProgress;
    private static final float ANIMATION_DURATION = 250f;

    private static final float LABEL_HEIGHT = 12f;
    private static final float SQUARE_SIZE = 50f;
    private static final float HUE_BAR_WIDTH = 10f;
    private static final float HUE_GAP = 4f;
    private static final float BLACK_BRI_EPSILON = 0.001f;
    private static final float GREY_SAT_EPSILON = 0.001f;
    private static final float ALPHA_BAR_WIDTH = 10f;
    private static final float ALPHA_GAP = 4f;
    private static final float SQUARE_TOP_PAD = 2f;
    private static final float BOTTOM_PAD = 2f;
    private static final int HUE_STEPS = 20;
    private static final float PREVIEW_BOX_SIZE = 5f;

    public ColorComponent(ColorSetting colorSetting, ModuleComponent moduleComponent, float o) {
        this.colorSetting = colorSetting;
        this.moduleComponent = moduleComponent;
        this.o = o;
        this.animationProgress = 0f;
        this.animationStartProgress = 0f;
        this.animationTargetProgress = 0f;
    }

    public float getExpandedHeight() {
        return LABEL_HEIGHT + SQUARE_TOP_PAD + SQUARE_SIZE + BOTTOM_PAD;
    }

    public float getAnimationProgress() {
        if (smoothTimer != null) {
            if (System.currentTimeMillis() - smoothTimer.last >= ANIMATION_DURATION + 30) {
                smoothTimer = null;
                animationProgress = animationTargetProgress;
                animationStartProgress = animationTargetProgress;
            } else {
                animationProgress = smoothTimer.getValueFloat(animationStartProgress, animationTargetProgress, 1);
                if (animationProgress == animationTargetProgress) {
                    smoothTimer = null;
                    animationStartProgress = animationTargetProgress;
                }
            }
        }
        return animationProgress;
    }

    @Override
    public void render() {
        float cx = moduleComponent.categoryComponent.getX();
        float cy = moduleComponent.categoryComponent.getY();
        float cw = moduleComponent.categoryComponent.getWidth();

        float boxX = cx + 4 + (xOffset / 2);
        float boxY = cy + o + 3f;
        RenderUtils.drawRect(boxX - 0.5, boxY - 0.5,
                boxX + PREVIEW_BOX_SIZE + 0.5, boxY + PREVIEW_BOX_SIZE + 0.5, 0xFF3C3C46);
        if (colorSetting.hasAlpha()) {
            int checkSize = 2;
            for (float px = boxX; px < boxX + PREVIEW_BOX_SIZE; px += checkSize) {
                for (float py = boxY; py < boxY + PREVIEW_BOX_SIZE; py += checkSize) {
                    int col = ((int) ((px - boxX) / checkSize) + (int) ((py - boxY) / checkSize)) % 2 == 0
                            ? 0xFF666666 : 0xFF999999;
                    RenderUtils.drawRect(px, py,
                            Math.min(px + checkSize, boxX + PREVIEW_BOX_SIZE),
                            Math.min(py + checkSize, boxY + PREVIEW_BOX_SIZE), col);
                }
            }
        }
        RenderUtils.drawRect(boxX, boxY,
                boxX + PREVIEW_BOX_SIZE, boxY + PREVIEW_BOX_SIZE,
                colorSetting.getColor());

        RavenFontRenderer renderer = Gui.getClickGuiSettingFontRenderer();
        GL11.glPushMatrix();
        GL11.glScaled(0.5, 0.5, 0.5);
        float textOffset = renderer.getStringWidth("[+]  ");
        renderer.drawString(
                colorSetting.getName(),
                (cx + 4) * 2 + xOffset + textOffset,
                (cy + o + 4) * 2,
                -1,
                true
        );
        GL11.glPopMatrix();

        float progress = getAnimationProgress();
        if (progress <= 0f) return;

        float scrollOffset = moduleComponent.categoryComponent.moduleY - cy;
        float contentTopScreen = cy + o + LABEL_HEIGHT + scrollOffset;
        float revealH = (getExpandedHeight() - LABEL_HEIGHT) * progress;
        RenderUtils.scissorPushGui(cx, contentTopScreen, cw, revealH);
        renderPickerContent(cx, cy);
        RenderUtils.scissorPop();
    }

    private void renderPickerContent(float cx, float cy) {
        float areaLeft = cx + 4 + (xOffset / 2);
        float sqTop = cy + o + LABEL_HEIGHT + SQUARE_TOP_PAD;
        float sqRight = areaLeft + SQUARE_SIZE;
        float sqBottom = sqTop + SQUARE_SIZE;

        float bri = (dragMode != 0) ? cachedBri : colorSetting.getBrightness();
        float satFromSetting = (dragMode != 0) ? cachedSat : colorSetting.getSaturation();
        boolean isBlack = bri < BLACK_BRI_EPSILON;
        boolean isGrey = satFromSetting < GREY_SAT_EPSILON;
        if (dragMode == 0 && !isBlack) {
            cachedBri = bri;
            cachedSat = colorSetting.getSaturation();
            if (!isGrey) {
                cachedHue = colorSetting.getHue();
            }
        }
        // When black or grey, RGBtoHSB gives hue=0; use cached hue so the hue bar doesn't jump to red
        boolean useCachedHue = dragMode != 0 || isBlack || isGrey;
        float hue = useCachedHue ? cachedHue / 360f : colorSetting.getHue() / 360f;
        float sat = (dragMode != 0 || isBlack) ? cachedSat : satFromSetting;

        int hueRGB = Color.HSBtoRGB(hue, 1f, 1f) | 0xFF000000;
        RenderUtils.drawRect(areaLeft, sqTop, sqRight, sqBottom, hueRGB);
        RenderUtils.drawHorizontalGradientRect(areaLeft, sqTop, sqRight, sqBottom,
                0xFFFFFFFF, 0x00FFFFFF);
        RenderUtils.drawVerticalGradientRect(areaLeft, sqTop, sqRight, sqBottom,
                0x00000000, 0xFF000000);

        RenderUtils.drawOutline(areaLeft - 1, sqTop - 1, sqRight + 1, sqBottom + 1,
                1f, 0xFF3C3C46);

        float indX = areaLeft + sat * SQUARE_SIZE;
        float indY = sqTop + (1f - bri) * SQUARE_SIZE;
        RenderUtils.drawRect(indX - 2, indY, indX + 3, indY + 1, 0xFFFFFFFF);
        RenderUtils.drawRect(indX, indY - 2, indX + 1, indY + 3, 0xFFFFFFFF);

        float hueLeft = sqRight + HUE_GAP;
        float hueRight = hueLeft + HUE_BAR_WIDTH;
        float stepH = SQUARE_SIZE / HUE_STEPS;
        for (int i = 0; i < HUE_STEPS; i++) {
            float h1 = (float) i / HUE_STEPS;
            float h2 = (float) (i + 1) / HUE_STEPS;
            int c1 = Color.HSBtoRGB(h1, 1f, 1f) | 0xFF000000;
            int c2 = Color.HSBtoRGB(h2, 1f, 1f) | 0xFF000000;
            RenderUtils.drawVerticalGradientRect(hueLeft, sqTop + i * stepH,
                    hueRight, sqTop + (i + 1) * stepH, c1, c2);
        }

        RenderUtils.drawOutline(hueLeft - 1, sqTop - 1, hueRight + 1, sqBottom + 1,
                1f, 0xFF3C3C46);

        float hueIndY = sqTop + Math.max(0, Math.min(1, hue)) * SQUARE_SIZE;
        RenderUtils.drawRect(hueLeft - 1, hueIndY - 1,
                hueRight + 1, hueIndY + 2, 0xFFFFFFFF);

        if (colorSetting.hasAlpha()) {
            float alphaLeft = hueRight + ALPHA_GAP;
            float alphaRight = alphaLeft + ALPHA_BAR_WIDTH;

            int checkSize = 4;
            for (float ax = alphaLeft; ax < alphaRight; ax += checkSize) {
                for (float ay = sqTop; ay < sqBottom; ay += checkSize) {
                    int col = ((int) ((ax - alphaLeft) / checkSize)
                            + (int) ((ay - sqTop) / checkSize)) % 2 == 0
                            ? 0xFF666666 : 0xFF999999;
                    RenderUtils.drawRect(ax, ay,
                            Math.min(ax + checkSize, alphaRight),
                            Math.min(ay + checkSize, sqBottom), col);
                }
            }

            int rgb = colorSetting.getRGB();
            RenderUtils.drawVerticalGradientRect(alphaLeft, sqTop, alphaRight, sqBottom,
                    rgb & 0x00FFFFFF, rgb | 0xFF000000);

            RenderUtils.drawOutline(alphaLeft - 1, sqTop - 1, alphaRight + 1, sqBottom + 1,
                    1f, 0xFF3C3C46);

            float alphaFrac = colorSetting.getAlpha() / 255f;
            float alphaIndY = sqTop + alphaFrac * SQUARE_SIZE;
            RenderUtils.drawRect(alphaLeft - 1, alphaIndY - 1,
                    alphaRight + 1, alphaIndY + 2, 0xFFFFFFFF);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY) {
        this.y = moduleComponent.categoryComponent.getModuleY() + this.o;
        this.x = moduleComponent.categoryComponent.getX();

        if (dragMode == 0 || getAnimationProgress() < 1f) return;

        float areaLeft = this.x + 4 + (xOffset / 2);
        float sqTop = this.y + LABEL_HEIGHT + SQUARE_TOP_PAD;
        float sqRight = areaLeft + SQUARE_SIZE;
        float sqBottom = sqTop + SQUARE_SIZE;
        float hueLeft = sqRight + HUE_GAP;
        float hueRight = hueLeft + HUE_BAR_WIDTH;

        if (dragMode == 1) {
            cachedSat = Math.max(0, Math.min(1, (mouseX - areaLeft) / SQUARE_SIZE));
            cachedBri = Math.max(0, Math.min(1, 1f - (mouseY - sqTop) / SQUARE_SIZE));
            colorSetting.setFromHSB(cachedHue, cachedSat, cachedBri);
            markUnsaved();
        } else if (dragMode == 2) {
            cachedHue = Math.max(0, Math.min(360, (mouseY - sqTop) / SQUARE_SIZE * 360f));
            colorSetting.setFromHSB(cachedHue, cachedSat, cachedBri);
            markUnsaved();
        } else if (dragMode == 3 && colorSetting.hasAlpha()) {
            float a = Math.max(0, Math.min(1, (mouseY - sqTop) / SQUARE_SIZE));
            colorSetting.setAlpha((int) (a * 255));
            markUnsaved();
        }
    }

    @Override
    public boolean onClick(int mouseX, int mouseY, int button) {
        if (!moduleComponent.isOpened || !moduleComponent.isVisible(this)) {
            return false;
        }

        float cw = moduleComponent.categoryComponent.getWidth();

        if (mouseX > this.x && mouseX < this.x + cw
                && mouseY > this.y && mouseY < this.y + LABEL_HEIGHT) {
            if (button == 0 || button == 1) {
                float currentProgress = getAnimationProgress();
                this.animationStartProgress = currentProgress;
                this.expanded = !this.expanded;
                this.animationTargetProgress = this.expanded ? 1f : 0f;
                (this.smoothTimer = new Timer(ANIMATION_DURATION)).start();
                moduleComponent.updateSettingPositions();
                return true;
            }
        }

        if (button != 0) return false;
        if (getAnimationProgress() < 1f) return false;

        float areaLeft = this.x + 4 + (xOffset / 2);
        float sqTop = this.y + LABEL_HEIGHT + SQUARE_TOP_PAD;
        float sqRight = areaLeft + SQUARE_SIZE;
        float sqBottom = sqTop + SQUARE_SIZE;
        float hueLeft = sqRight + HUE_GAP;
        float hueRight = hueLeft + HUE_BAR_WIDTH;

        if (mouseX >= areaLeft && mouseX <= sqRight
                && mouseY >= sqTop && mouseY <= sqBottom) {
            cacheHSB();
            dragMode = 1;
            return false;
        }

        if (mouseX >= hueLeft - 2 && mouseX <= hueRight + 2
                && mouseY >= sqTop && mouseY <= sqBottom) {
            cacheHSB();
            dragMode = 2;
            return false;
        }

        if (colorSetting.hasAlpha()) {
            float alphaLeft = hueRight + ALPHA_GAP;
            float alphaRight = alphaLeft + ALPHA_BAR_WIDTH;
            if (mouseX >= alphaLeft - 2 && mouseX <= alphaRight + 2
                    && mouseY >= sqTop && mouseY <= sqBottom) {
                cacheHSB();
                dragMode = 3;
                return false;
            }
        }

        return false;
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY, int button) {
        dragMode = 0;
    }

    @Override
    public void onGuiClosed() {
        dragMode = 0;
        smoothTimer = null;
        animationProgress = expanded ? 1f : 0f;
        animationStartProgress = animationProgress;
        animationTargetProgress = animationProgress;
    }

    @Override
    public void updateHeight(float n) {
        this.o = n;
    }

    @Override
    public float getOffset() {
        return this.o;
    }

    @Override
    public boolean isBaseVisible() {
        return colorSetting.visible;
    }

    public void restoreExpandedState(boolean expanded) {
        this.expanded = expanded;
        this.smoothTimer = null;
        this.animationProgress = expanded ? 1f : 0f;
        this.animationStartProgress = this.animationProgress;
        this.animationTargetProgress = this.animationProgress;
    }

    private void cacheHSB() {
        float bri = colorSetting.getBrightness();
        float sat = colorSetting.getSaturation();
        cachedBri = bri;
        if (bri >= BLACK_BRI_EPSILON) {
            cachedSat = sat;
            if (sat >= GREY_SAT_EPSILON) {
                cachedHue = colorSetting.getHue();
            }
        }
    }

    private void markUnsaved() {
        if (Raven.currentProfile != null) {
            Raven.currentProfile.getModule().saved = false;
        }
    }
}
