package keystrokesmod.module.impl.render;

import keystrokesmod.mixin.impl.accessor.IAccessorShaderGroup;
import keystrokesmod.mixin.interfaces.ISaturationRenderer;
import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.SliderSetting;
import net.minecraft.client.shader.Shader;
import net.minecraft.client.shader.ShaderGroup;
import net.minecraft.client.shader.ShaderUniform;
import net.minecraft.util.ResourceLocation;

import java.io.IOException;
import java.util.List;

public class Saturation extends Module {
    private static final ResourceLocation SHADER_LOCATION = new ResourceLocation("minecraft:shaders/post/color_convolve.json");

    private SliderSetting saturationSlider;
    private float lastSaturation = 1.0f;

    public Saturation() {
        super("Saturation", category.render);
        this.registerSetting(saturationSlider = new SliderSetting("Saturation", 1.0, -1.0, 5.0, 0.05));
    }

    @Override
    public void onEnable() {
        loadShader();
    }

    @Override
    public void onDisable() {
        removeShader();
    }

    @Override
    public void onUpdate() {
        float current = (float) saturationSlider.getInput();
        if (!isShaderActive()) {
            loadShader();
        }
        if (current != lastSaturation) {
            lastSaturation = current;
            applySaturation();
        }
    }

    private void loadShader() {
        if (mc.theWorld == null) return;
        if (isShaderActive()) return;
        try {
            ShaderGroup shader = new ShaderGroup(
                    mc.getTextureManager(),
                    mc.getResourceManager(),
                    mc.getFramebuffer(),
                    SHADER_LOCATION
            );
            shader.createBindFramebuffers(mc.displayWidth, mc.displayHeight);
            ISaturationRenderer ren = getSaturationRenderer();
            if (ren != null) ren.raven$setSaturationShader(shader);
            lastSaturation = (float) saturationSlider.getInput();
            applySaturation();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void removeShader() {
        ISaturationRenderer renderer = getSaturationRenderer();
        if (renderer == null) return;
        ShaderGroup shader = renderer.raven$getSaturationShader();
        if (shader != null) {
            shader.deleteShaderGroup();
        }
        renderer.raven$setSaturationShader(null);
    }

    private void applySaturation() {
        ISaturationRenderer renderer = getSaturationRenderer();
        if (renderer == null) return;
        ShaderGroup shader = renderer.raven$getSaturationShader();
        if (shader == null) return;
        List<Shader> shaders = ((IAccessorShaderGroup) shader).getListShaders();
        if (shaders == null) return;
        for (Shader s : shaders) {
            ShaderUniform su = s.getShaderManager().getShaderUniform("Saturation");
            if (su != null) {
                su.set(lastSaturation);
            }
        }
    }

    private boolean isShaderActive() {
        ISaturationRenderer renderer = getSaturationRenderer();
        return renderer != null && renderer.raven$getSaturationShader() != null;
    }

    private ISaturationRenderer getSaturationRenderer() {
        if (mc.entityRenderer == null) return null;
        return (ISaturationRenderer) mc.entityRenderer;
    }
}
