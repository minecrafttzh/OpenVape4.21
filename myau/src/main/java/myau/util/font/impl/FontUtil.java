package myau.util.font.impl;

import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.ResourceLocation;

import java.awt.*;
import java.io.File;
import java.io.InputStream;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static myau.config.Config.mc;

public class FontUtil {

    public static Font getResource(Map<String, Font> locationMap, String location, int size) {
        Font font;

        ScaledResolution sr = new ScaledResolution(mc);

        size = (int) (size * ((double) sr.getScaleFactor() / 2));

        try {
            if (locationMap.containsKey(location)) {
                font = locationMap.get(location).deriveFont(Font.PLAIN, size);
            } else {
                InputStream is = mc.getResourceManager().getResource(new ResourceLocation("myau:font/" + location)).getInputStream();
                locationMap.put(location, font = Font.createFont(0, is));
                font = font.deriveFont(Font.PLAIN, size);
            }
        } catch (Exception exception) {
            for (File file : getDevFontFiles(location)) {
                if (!file.isFile()) {
                    continue;
                }
                try {
                    locationMap.put(location, font = Font.createFont(0, file));
                    return font.deriveFont(Font.PLAIN, size);
                } catch (Exception ignored) {
                }
            }

            System.err.println("[Myau] Failed to load font: " + location);
            font = new Font("default", Font.PLAIN, size);
            locationMap.put(location, font);
        }
        return font;
    }

    private static Set<File> getDevFontFiles(String location) {
        Set<File> files = new LinkedHashSet<>();
        addDevFontFiles(files, new File(System.getProperty("user.dir")), location);

        try {
            File codeSource = new File(FontUtil.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            addDevFontFiles(files, codeSource, location);
        } catch (Exception ignored) {
        }

        return files;
    }

    private static void addDevFontFiles(Set<File> files, File start, String location) {
        File current = start;
        if (current != null && current.isFile()) {
            current = current.getParentFile();
        }

        for (int i = 0; current != null && i < 8; i++) {
            files.add(new File(current, "src/main/resources/assets/myau/font/" + location));
            files.add(new File(current, "assets/myau/font/" + location));
            current = current.getParentFile();
        }
    }
}
