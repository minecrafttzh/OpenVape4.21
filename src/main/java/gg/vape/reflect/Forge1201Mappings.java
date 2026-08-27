package gg.vape.reflect;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Resolves MCP/SRG names against the regular Forge 1.20.1 runtime.
 * Regular Forge may run with either SRG member names (m_91087_/f_90981_)
 * or official Mojmap names (getInstance/instance) depending on the loader
 * configuration, so both naming schemes are accepted during detection.
 * Class names are always official (net.minecraft...) paths.
 */
public final class Forge1201Mappings {
    private static final VanillaSrgMappings MOJMAP = new VanillaSrgMappings(
            "Minecraft 1.20.1 Forge (mojmap)",
            "/mappings/neoforge1201/joined.srg",
            "net/minecraft/client/Minecraft",
            "getInstance",
            "instance",
            "net/minecraft/client/multiplayer/ClientLevel",
            "net/minecraft/client/player/LocalPlayer",
            "net/minecraft/client/player/AbstractClientPlayer",
            "net/minecraft/world/level/Level");

    private static final VanillaSrgMappings SRG = new VanillaSrgMappings(
            "Minecraft 1.20.1 Forge (SRG)",
            "/mappings/neoforge1201/joined.srg",
            "net/minecraft/client/Minecraft",
            "m_91087_",
            "f_90981_",
            "net/minecraft/client/multiplayer/ClientLevel",
            "net/minecraft/client/player/LocalPlayer",
            "net/minecraft/client/player/AbstractClientPlayer",
            "net/minecraft/world/level/Level");

    private Forge1201Mappings() {
    }

    public static String remapClassName(String sourceClassName) {
        String result = MOJMAP.remapClassName(sourceClassName);
        if (result != null) {
            return result;
        }
        return SRG.remapClassName(sourceClassName);
    }

    public static Class<?> resolveClass(String sourceClassName,
                                        ClassLoader... preferredLoaders) {
        Class<?> result = MOJMAP.resolveClass(sourceClassName, preferredLoaders);
        if (result != null) {
            return result;
        }
        return SRG.resolveClass(sourceClassName, preferredLoaders);
    }

    public static boolean isRuntimePresent(ClassLoader... preferredLoaders) {
        return MOJMAP.isRuntimePresent(preferredLoaders)
                || SRG.isRuntimePresent(preferredLoaders);
    }

    static String lookupFieldSrgName(Field field) {
        String result = MOJMAP.lookupFieldSrgName(field);
        if (result != null) {
            return result;
        }
        return SRG.lookupFieldSrgName(field);
    }

    static String lookupMethodSrgName(Method method) {
        String result = MOJMAP.lookupMethodSrgName(method);
        if (result != null) {
            return result;
        }
        return SRG.lookupMethodSrgName(method);
    }

    public static int getClassMappingCount() {
        return Math.max(MOJMAP.getClassMappingCount(),
                SRG.getClassMappingCount());
    }

    public static int getFieldMappingCount() {
        return Math.max(MOJMAP.getFieldMappingCount(),
                SRG.getFieldMappingCount());
    }

    public static int getMethodMappingCount() {
        return Math.max(MOJMAP.getMethodMappingCount(),
                SRG.getMethodMappingCount());
    }
}
