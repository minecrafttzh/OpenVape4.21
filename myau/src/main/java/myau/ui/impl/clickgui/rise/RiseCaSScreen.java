package myau.ui.impl.clickgui.rise;

import myau.config.Config;
import myau.util.AnimationUtil;
import myau.util.RenderUtil;
import myau.util.font.FontManager;
import org.lwjgl.input.Keyboard;

import java.awt.Color;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class RiseCaSScreen {

    private final List<ConfigCard> userConfigs = new ArrayList<ConfigCard>();
    private float scrollOffset;
    private float targetScroll;
    private boolean needsRefresh = true;
    private boolean creatingNew;
    private String newConfigName = "";
    private float lastContentHeight;

    public void refresh() {
        userConfigs.clear();
        File configDir = new File("./config/Myau/");
        File[] files = configDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".json"));
        if (files != null) {
            for (File file : files) {
                userConfigs.add(new ConfigCard(file.getName().substring(0, file.getName().length() - 5)));
            }
        }

        Collections.sort(userConfigs, new Comparator<ConfigCard>() {
            @Override
            public int compare(ConfigCard first, ConfigCard second) {
                return first.name.compareToIgnoreCase(second.name);
            }
        });
        needsRefresh = false;
    }

    public void resetScroll() {
        scrollOffset = 0;
        targetScroll = 0;
    }

    public void draw(float x, float y, float w, float h, int mouseX, int mouseY, float deltaTime) {
        if (needsRefresh) refresh();

        updateScroll(deltaTime);

        float cardW = (w - 28f) / 3f;
        float cardH = 50f;
        float gap = 7f;
        float rowH = 57f;
        float curY = y + 8f - scrollOffset;

        if (FontManager.productSans20 != null) {
            FontManager.productSans20.drawString("Your Configs", x + 7, curY, RiseColors.TEXT.getRGB());
        }
        if (FontManager.productSans12 != null) {
            String count = String.valueOf(userConfigs.size());
            float titleWidth = FontManager.productSans20 != null
                    ? (float) FontManager.productSans20.getStringWidth("Your Configs") : 80f;
            FontManager.productSans12.drawString(count, x + 13 + titleWidth, curY + 4,
                    RiseClickGUI.accent().getRGB());
        }

        curY += 24f;
        for (int i = 0; i < userConfigs.size(); i++) {
            int col = i % 3;
            int row = i / 3;
            float cardX = x + 7 + col * (cardW + gap);
            float cardY = curY + row * rowH;
            if (cardY + cardH >= y && cardY <= y + h) {
                userConfigs.get(i).draw(cardX, cardY, cardW, cardH, mouseX, mouseY, deltaTime);
            }
        }

        int rows = (int) Math.ceil(userConfigs.size() / 3.0);
        curY += rows * rowH + 12f;

        drawSaveButton(x + 7, curY, w - 14, 30, mouseX, mouseY);
        curY += 42f;

        if (FontManager.productSans12 != null && curY > y && curY < y + h) {
            FontManager.productSans12.drawString("Left click loads. Right click deletes.", x + 7, curY,
                    RiseColors.TEXT_TRINARY.getRGB());
        }

        lastContentHeight = curY - y + scrollOffset + 16f;
    }

    private void drawSaveButton(float x, float y, float w, float h, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
        RenderUtil.drawRoundedRect(x, y, w, h, 6, new Color(0, 0, 0, hovered ? 72 : 45).getRGB(),
                true, true, true, true);

        if (FontManager.productSans16 != null) {
            if (creatingNew) {
                String cursor = System.currentTimeMillis() % 1000 < 500 ? "|" : "";
                FontManager.productSans16.drawString("Name: " + newConfigName + cursor, x + 10, y + 8,
                        RiseColors.TEXT.getRGB());
            } else {
                FontManager.productSans16.drawString("+ Save New Config", x + 10, y + 8,
                        hovered ? RiseColors.TEXT.getRGB() : RiseColors.TEXT_TRINARY.getRGB());
            }
        }
    }

    public boolean click(float x, float y, float w, float h, int mouseX, int mouseY, int button) {
        if (needsRefresh) refresh();

        float cardW = (w - 28f) / 3f;
        float cardH = 50f;
        float gap = 7f;
        float rowH = 57f;
        float curY = y + 8f - scrollOffset + 24f;

        for (int i = 0; i < userConfigs.size(); i++) {
            int col = i % 3;
            int row = i / 3;
            float cardX = x + 7 + col * (cardW + gap);
            float cardY = curY + row * rowH;
            if (mouseX >= cardX && mouseX <= cardX + cardW && mouseY >= cardY && mouseY <= cardY + cardH) {
                ConfigCard card = userConfigs.get(i);
                if (button == 0) {
                    new Config(card.name, false).load();
                    card.flashAnimation = 1f;
                    return true;
                }
                if (button == 1) {
                    File file = new File("./config/Myau/", card.name + ".json");
                    if (file.exists()) file.delete();
                    needsRefresh = true;
                    return true;
                }
            }
        }

        int rows = (int) Math.ceil(userConfigs.size() / 3.0);
        curY += rows * rowH + 12f;
        float buttonX = x + 7;
        float buttonW = w - 14;
        if (button == 0 && mouseX >= buttonX && mouseX <= buttonX + buttonW && mouseY >= curY && mouseY <= curY + 30) {
            if (creatingNew && !newConfigName.trim().isEmpty()) {
                saveNew();
            } else {
                creatingNew = true;
                newConfigName = "";
            }
            return true;
        }
        return false;
    }

    public void key(char typedChar, int keyCode) {
        if (!creatingNew) return;

        if (keyCode == Keyboard.KEY_ESCAPE) {
            creatingNew = false;
            newConfigName = "";
            return;
        }
        if (keyCode == Keyboard.KEY_RETURN) {
            saveNew();
            return;
        }
        if (keyCode == Keyboard.KEY_BACK) {
            if (!newConfigName.isEmpty()) newConfigName = newConfigName.substring(0, newConfigName.length() - 1);
            return;
        }
        if (typedChar >= ' ' && typedChar <= '~' && newConfigName.length() < 24) {
            if ("<>:\"/\\|?*".indexOf(typedChar) < 0) {
                newConfigName += typedChar;
            }
        }
    }

    public boolean isTyping() {
        return creatingNew;
    }

    public float getLastContentHeight() {
        return lastContentHeight;
    }

    public float getScrollOffset() {
        return scrollOffset;
    }

    public void scroll(int delta, float viewportHeight) {
        targetScroll += delta > 0 ? -20 : 20;
        targetScroll = Math.max(0, Math.min(targetScroll, maxScroll(viewportHeight)));
    }

    public void updateScroll(float deltaTime) {
        scrollOffset = AnimationUtil.animateSmooth(targetScroll, scrollOffset, 12f, deltaTime);
    }

    private void saveNew() {
        String name = newConfigName.trim();
        if (!name.isEmpty()) {
            new Config(name, true).save();
            creatingNew = false;
            newConfigName = "";
            needsRefresh = true;
        }
    }

    private float maxScroll(float viewportHeight) {
        return Math.max(0, lastContentHeight - viewportHeight);
    }

    private static class ConfigCard {
        private final String name;
        private float hoverAnimation;
        private float flashAnimation;

        private ConfigCard(String name) {
            this.name = name;
        }

        private void draw(float x, float y, float w, float h, int mouseX, int mouseY, float deltaTime) {
            boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
            hoverAnimation = AnimationUtil.animateSmooth(hovered ? 1f : 0f, hoverAnimation, 10f, deltaTime);
            flashAnimation = AnimationUtil.animateSmooth(0f, flashAnimation, 5f, deltaTime);

            RenderUtil.drawRoundedRect(x, y, w, h, 6,
                    new Color(0, 0, 0, (int) (42 + hoverAnimation * 24)).getRGB(),
                    true, true, true, true);

            if (flashAnimation > 0.01f) {
                Color accent = RiseClickGUI.accent();
                RenderUtil.drawRoundedRect(x, y, w, h, 6,
                        new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), (int) (flashAnimation * 60)).getRGB(),
                        true, true, true, true);
            }

            if (FontManager.productSans16 != null) {
                String display = trim(name, w - 10);
                float textWidth = (float) FontManager.productSans16.getStringWidth(display);
                FontManager.productSans16.drawString(display, x + w / 2f - textWidth / 2f, y + 15,
                        RiseColors.TEXT.getRGB());
            }
            if (FontManager.productSans12 != null) {
                String sub = "Click to load";
                float subWidth = (float) FontManager.productSans12.getStringWidth(sub);
                FontManager.productSans12.drawString(sub, x + w / 2f - subWidth / 2f, y + 30,
                        RiseColors.TEXT_TRINARY.getRGB());
            }
        }

        private String trim(String text, float width) {
            if (FontManager.productSans16 == null || FontManager.productSans16.getStringWidth(text) <= width) return text;
            String result = text;
            while (result.length() > 1 && FontManager.productSans16.getStringWidth(result + "...") > width) {
                result = result.substring(0, result.length() - 1);
            }
            return result + "...";
        }
    }
}
