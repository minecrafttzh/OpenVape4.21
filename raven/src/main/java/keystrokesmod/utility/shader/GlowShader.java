package keystrokesmod.utility.shader;

import org.lwjgl.opengl.GL20;

public class GlowShader extends OutlineESPShader {
    private static final String FRAG = "#version 120\n" +
            "uniform sampler2D tex;\n" +
            "uniform vec4 tint;\n" +
            "void main() {\n" +
            "  float a = texture2D(tex, gl_TexCoord[0].xy).a;\n" +
            "  gl_FragColor = vec4(tint.rgb, a > 0.0 ? tint.a : 0.0);\n" +
            "}";

    public GlowShader() {
        super(FRAG);
    }

    @Override
    public void onLink() {
        cacheUniform("tex");
        cacheUniform("tint");
    }

    @Override
    public void onUse() {
        GL20.glUseProgram(programId);
        int u = uniform("tex");
        if (u >= 0) GL20.glUniform1i(u, 0);
    }

    public void setColor(int r, int g, int b, int a) {
        int u = uniform("tint");
        if (u >= 0) GL20.glUniform4f(u, r / 255f, g / 255f, b / 255f, a / 255f);
    }

    public void setColorFromARGB(int argb) {
        setColor((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, (argb >> 24) & 0xFF);
    }
}
