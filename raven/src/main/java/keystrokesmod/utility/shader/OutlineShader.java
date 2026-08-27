package keystrokesmod.utility.shader;

import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL20;

public class OutlineShader extends OutlineESPShader {
    private static final String FRAG = "#version 120\n" +
            "uniform sampler2D tex;\n" +
            "uniform vec2 texelSize;\n" +
            "uniform float kernel;\n" +
            "void main() {\n" +
            "  vec4 c = texture2D(tex, gl_TexCoord[0].xy);\n" +
            "  if (c.a > 0.0) { gl_FragColor = vec4(0.0); return; }\n" +
            "  gl_FragColor = vec4(0.0);\n" +
            "  for (float dx = -kernel; dx <= kernel; dx += 1.0)\n" +
            "    for (float dy = -kernel; dy <= kernel; dy += 1.0) {\n" +
            "      vec4 n = texture2D(tex, gl_TexCoord[0].xy + vec2(dx, dy) * texelSize);\n" +
            "      if (n.a > 0.0) gl_FragColor = n;\n" +
            "    }\n" +
            "}";

    public OutlineShader() {
        super(FRAG);
    }

    @Override
    public void onLink() {
        cacheUniform("tex");
        cacheUniform("texelSize");
        cacheUniform("kernel");
    }

    @Override
    public void onUse() {
        GL20.glUseProgram(programId);
        int u = uniform("tex");
        if (u >= 0) GL20.glUniform1i(u, 0);
        u = uniform("texelSize");
        if (u >= 0) {
            Minecraft mc = Minecraft.getMinecraft();
            GL20.glUniform2f(u, 1.0f / mc.displayWidth, 1.0f / mc.displayHeight);
        }
        u = uniform("kernel");
        if (u >= 0) GL20.glUniform1f(u, 2.0f);
    }
}
