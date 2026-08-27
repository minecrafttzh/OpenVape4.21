package myau.util.shader;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

import java.util.HashMap;
import java.util.Map;

public class ShaderUtil {
    // 默认顶点着色器 (通用)
    private static final String DEFAULT_VERTEX_SHADER =
            "#version 120\n" +
                    "\n" +
                    "void main() {\n" +
                    "    gl_TexCoord[0] = gl_MultiTexCoord0;\n" +
                    "    gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;\n" +
                    "}";

    private final Map<String, Integer> uniforms = new HashMap<>();
    private int programID;

    /**
     * 仅使用片元着色器源代码初始化（使用默认顶点着色器）
     * @param fragmentSource 片元着色器 GLSL 代码
     */
    public ShaderUtil(String fragmentSource) {
        this(DEFAULT_VERTEX_SHADER, fragmentSource);
    }

    public static void drawQuads() {
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0, 0);
        GL11.glVertex2f(-1, -1);
        GL11.glTexCoord2f(0, 1);
        GL11.glVertex2f(-1, 1);
        GL11.glTexCoord2f(1, 1);
        GL11.glVertex2f(1, 1);
        GL11.glTexCoord2f(1, 0);
        GL11.glVertex2f(1, -1);
        GL11.glEnd();
    }

    /**
     * 使用自定义顶点和片元着色器源代码初始化
     * @param vertexSource 顶点着色器 GLSL 代码
     * @param fragmentSource 片元着色器 GLSL 代码
     */
    public ShaderUtil(String vertexSource, String fragmentSource) {
        int program = 0;
        try {
            int vertexShaderID = createShader(vertexSource, GL20.GL_VERTEX_SHADER);
            int fragmentShaderID = createShader(fragmentSource, GL20.GL_FRAGMENT_SHADER);

            if (vertexShaderID == 0 || fragmentShaderID == 0) {
                this.programID = 0;
                return;
            }

            program = GL20.glCreateProgram();
            GL20.glAttachShader(program, vertexShaderID);
            GL20.glAttachShader(program, fragmentShaderID);
            GL20.glLinkProgram(program);

            int status = GL20.glGetProgrami(program, GL20.GL_LINK_STATUS);
            if (status == 0) {
                System.err.println("Shader Link Failed: " + GL20.glGetProgramInfoLog(program, 1024));
                program = 0;
            }

            GL20.glValidateProgram(program);
            GL20.glDeleteShader(vertexShaderID);
            GL20.glDeleteShader(fragmentShaderID);

        } catch (Exception e) {
            e.printStackTrace();
            program = 0;
        }

        this.programID = program;
    }

    private int createShader(String shaderSource, int shaderType) {
        int shaderID = 0;
        try {
            shaderID = GL20.glCreateShader(shaderType);
            GL20.glShaderSource(shaderID, shaderSource);
            GL20.glCompileShader(shaderID);

            if (GL20.glGetShaderi(shaderID, GL20.GL_COMPILE_STATUS) == 0) {
                System.err.println("Error compiling shader. Type: " + shaderType);
                System.err.println(GL20.glGetShaderInfoLog(shaderID, 1024));
                GL20.glDeleteShader(shaderID);
                return 0;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
        return shaderID;
    }

    public void init() {
        if (programID != 0) {
            GL20.glUseProgram(programID);
        }
    }

    public void unload() {
        if (programID != 0) {
            GL20.glUseProgram(0);
        }
    }

    public int getUniform(String name) {
        if (programID == 0) return -1;
        if (uniforms.containsKey(name)) {
            return uniforms.get(name);
        }
        int uniform = GL20.glGetUniformLocation(programID, name);
        uniforms.put(name, uniform);
        return uniform;
    }

    public void setUniformf(String name, float... args) {
        if (programID == 0) return;
        int uniform = getUniform(name);
        if (uniform == -1) return;

        switch (args.length) {
            case 1: GL20.glUniform1f(uniform, args[0]); break;
            case 2: GL20.glUniform2f(uniform, args[0], args[1]); break;
            case 3: GL20.glUniform3f(uniform, args[0], args[1], args[2]); break;
            case 4: GL20.glUniform4f(uniform, args[0], args[1], args[2], args[3]); break;
        }
    }

    public void setUniformi(String name, int... args) {
        if (programID == 0) return;
        int uniform = getUniform(name);
        if (uniform == -1) return;

        switch (args.length) {
            case 1: GL20.glUniform1i(uniform, args[0]); break;
            case 2: GL20.glUniform2i(uniform, args[0], args[1]); break;
            case 3: GL20.glUniform3i(uniform, args[0], args[1], args[2]); break;
            case 4: GL20.glUniform4i(uniform, args[0], args[1], args[2], args[3]); break;
        }
    }

    public int getProgramID() {
        return this.programID;
    }

    public void delete() {
        if (this.programID != 0) {
            GL20.glDeleteProgram(this.programID);
        }
    }
}
