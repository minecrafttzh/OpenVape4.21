//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package myau.property.properties;

import com.google.gson.JsonObject;
import java.awt.Desktop;
import java.io.File;
import java.util.function.BooleanSupplier;
import myau.property.Property;

public class FileProperty extends Property<String> {
    private final File file;

    public FileProperty(String name, File file) {
        this(name, file, (BooleanSupplier)null);
    }

    public FileProperty(String name, File file, BooleanSupplier booleanSupplier) {
        super(name, file.getPath(), booleanSupplier);
        this.file = file;
    }

    public File getFile() {
        return this.file;
    }

    public void openFile() {
        try {
            if (!this.file.exists()) {
                this.file.createNewFile();
            }

            Desktop.getDesktop().open(this.file);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public String getValuePrompt() {
        return "file";
    }

    public String formatValue() {
        return String.format("&7[&aOpen File&7]");
    }

    public boolean parseString(String string) {
        return false;
    }

    public boolean read(JsonObject jsonObject) {
        return true;
    }

    public void write(JsonObject jsonObject) {
    }
}
