package keystrokesmod.module.impl.render;

import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.ColorSetting;
import net.minecraft.entity.EntityLivingBase;

public class DamageTint extends Module {
    public static DamageTint instance;

    public final ColorSetting color;
    public final ButtonSetting fade;

    public DamageTint() {
        super("Damage Tint", category.render, 0);
        this.registerSetting(color = new ColorSetting("Tint color", 255, 0, 0, 76));
        this.registerSetting(fade = new ButtonSetting("Fade out", false));
    }

    @Override
    public void onEnable() {
        instance = this;
    }

    @Override
    public void onDisable() {
        instance = null;
    }

    public static float computeAlpha(EntityLivingBase entity) {
        if (instance == null || !instance.fade.isToggled()) {
            return instance.color.getAlpha() / 255.0f;
        }
        float maxHurt = entity.maxHurtTime;
        if (maxHurt <= 0) return instance.color.getAlpha() / 255.0f;
        float percent = 1.0f - (float) entity.hurtTime / maxHurt;
        percent = (percent < 0.5f) ? (percent / 0.5f) : ((1.0f - percent) / 0.5f);
        return (instance.color.getAlpha() / 255.0f) * percent;
    }
}
