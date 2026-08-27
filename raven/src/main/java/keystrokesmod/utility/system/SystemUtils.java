package keystrokesmod.utility.system;

import keystrokesmod.utility.Utils;

import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.math.BigInteger;
import java.security.MessageDigest;

public class SystemUtils {
    public static String getHardwareIdForLoad(String url) {
        String hashedId = "";
        try {
            MessageDigest instance = MessageDigest.getInstance("MD5");
            instance.update(((System.currentTimeMillis() / 20000L + 29062381L) + "J{LlrPhHgj8zy:uB").getBytes("UTF-8"));
            hashedId = String.format("%032x", new BigInteger(1, instance.digest()));
            instance.update((System.getenv("COMPUTERNAME") + System.getenv("PROCESSOR_IDENTIFIER") + System.getenv("PROCESSOR_LEVEL") + Runtime.getRuntime().availableProcessors() + url).getBytes("UTF-8"));
            return hashedId;
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
        return hashedId;
    }

    public static void addToClipboard(String string) {
        try {
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            StringSelection stringSelection = new StringSelection(string);
            clipboard.setContents(stringSelection, null);
        }
        catch (Exception e) {
            Utils.sendMessage("&cFailed to copy &b" + string);
        }
    }
}