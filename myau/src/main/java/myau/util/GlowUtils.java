package myau.util;

import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;

public class GlowUtils { // we always love ai right(no)? credit: ChatGPT and Horaizion

    private static final int MAX_CACHE = 40;

    private static final Map<String, Integer> cache = new LinkedHashMap<String, Integer>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Integer> eldest) {
            if (size() > MAX_CACHE) {
                GL11.glDeleteTextures(eldest.getValue());
                return true;
            }
            return false;
        }
    };

    private static float[] createKernel(int radius) {
        int r = Math.max(radius, 2);
        int size = r * 2 + 1;
        float[] kernel = new float[size];

        float sigma = r / 2f;
        float sigma22 = 2f * sigma * sigma;

        float total = 0f;

        for (int i = -r; i <= r; i++) {
            float distance = i * i;
            float value = (float) Math.exp(-distance / sigma22);

            kernel[i + r] = value;
            total += value;
        }

        for (int i = 0; i < kernel.length; i++) {
            kernel[i] /= total;
        }

        return kernel;
    }

    private static BufferedImage blur(BufferedImage image, int radius) {
        int width = image.getWidth();
        int height = image.getHeight();

        float[] kernel = createKernel(radius);
        int r = radius;

        BufferedImage temp = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        BufferedImage output = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        // Horizontal blur
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {

                float a = 0, red = 0, green = 0, blue = 0;

                for (int k = -r; k <= r; k++) {
                    int px = Math.max(0, Math.min(width - 1, x + k));
                    int rgb = image.getRGB(px, y);
                    float weight = kernel[k + r];

                    a += ((rgb >> 24) & 255) * weight;
                    red += ((rgb >> 16) & 255) * weight;
                    green += ((rgb >> 8) & 255) * weight;
                    blue += (rgb & 255) * weight;
                }

                int result =
                        ((int)a << 24) |
                                ((int)red << 16) |
                                ((int)green << 8) |
                                (int)blue;

                temp.setRGB(x, y, result);
            }
        }

        // Vertical blur
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {

                float a = 0, red = 0, green = 0, blue = 0;

                for (int k = -r; k <= r; k++) {
                    int py = Math.max(0, Math.min(height - 1, y + k));
                    int rgb = temp.getRGB(x, py);
                    float weight = kernel[k + r];

                    a += ((rgb >> 24) & 255) * weight;
                    red += ((rgb >> 16) & 255) * weight;
                    green += ((rgb >> 8) & 255) * weight;
                    blue += (rgb & 255) * weight;
                }

                int result =
                        ((int)a << 24) |
                                ((int)red << 16) |
                                ((int)green << 8) |
                                (int)blue;

                output.setRGB(x, y, result);
            }
        }

        return output;
    }

    public static void drawGlow(float x, float y, float w, float h, int radius, Color color) {

        // IMPORTANT: remove x,y from cache key
        String key = w + "_" + h + "_" + radius + "_" + color.getRGB();

        Integer texture = cache.get(key);

        if (texture == null) {

            int imgW = (int) w + radius * 2;
            int imgH = (int) h + radius * 2;

            BufferedImage image = new BufferedImage(imgW, imgH, BufferedImage.TYPE_INT_ARGB);

            Graphics2D g = image.createGraphics();
            g.setColor(color);
            g.fillRoundRect(radius, radius, (int) w, (int) h, radius, radius);
            g.dispose();

            BufferedImage blurred = blur(image, radius);

            texture = GL11.glGenTextures();
            cache.put(key, texture);

            GlStateManager.bindTexture(texture);

            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);

            int[] pixels = new int[blurred.getWidth() * blurred.getHeight()];
            blurred.getRGB(0, 0, blurred.getWidth(), blurred.getHeight(), pixels, 0, blurred.getWidth());

            ByteBuffer buffer = BufferUtils.createByteBuffer(pixels.length * 4);

            for (int pixel : pixels) {
                buffer.put((byte)((pixel >> 16) & 255));
                buffer.put((byte)((pixel >> 8) & 255));
                buffer.put((byte)(pixel & 255));
                buffer.put((byte)((pixel >> 24) & 255));
            }

            buffer.flip();

            GL11.glTexImage2D(
                    GL11.GL_TEXTURE_2D,
                    0,
                    GL11.GL_RGBA,
                    blurred.getWidth(),
                    blurred.getHeight(),
                    0,
                    GL11.GL_RGBA,
                    GL11.GL_UNSIGNED_BYTE,
                    buffer
            );
        }

        GlStateManager.enableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.enableTexture2D();

        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(1f, 1f, 1f, 1f);

        GlStateManager.bindTexture(texture);

        GL11.glBegin(GL11.GL_QUADS);

        GL11.glTexCoord2f(0,0);
        GL11.glVertex2f(x - radius, y - radius);

        GL11.glTexCoord2f(1,0);
        GL11.glVertex2f(x + w + radius, y - radius);

        GL11.glTexCoord2f(1,1);
        GL11.glVertex2f(x + w + radius, y + h + radius);

        GL11.glTexCoord2f(0,1);
        GL11.glVertex2f(x - radius, y + h + radius);

        GL11.glEnd();

        GlStateManager.bindTexture(0);

        GlStateManager.enableAlpha();
        GlStateManager.disableBlend();
        GL11.glColor4f(1f,1f,1f,1f);
    }
}