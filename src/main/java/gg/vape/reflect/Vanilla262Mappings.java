package gg.vape.reflect;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;

/** Resolves official names in the unobfuscated Minecraft 26.2 client. */
public final class Vanilla262Mappings {
    private static final String MINECRAFT_CLASS =
            "net/minecraft/client/Minecraft";
    private static final String CLIENT_LEVEL_CLASS =
            "net/minecraft/client/multiplayer/ClientLevel";
    private static final String LOCAL_PLAYER_CLASS =
            "net/minecraft/client/player/LocalPlayer";
    private static final String ABSTRACT_PLAYER_CLASS =
            "net/minecraft/client/player/AbstractClientPlayer";
    private static final String LEVEL_CLASS =
            "net/minecraft/world/level/Level";

    private Vanilla262Mappings() {
    }

    public static Class<?> resolveClass(String sourceClassName,
                                        ClassLoader... preferredLoaders) {
        String internalName = normalizeInternalName(sourceClassName);
        if (internalName == null || internalName.isEmpty()) {
            return null;
        }
        for (ClassLoader loader : candidateLoaders(preferredLoaders)) {
            Class<?> resolved = resolveRuntimeClass(internalName, loader);
            if (resolved != null) {
                return resolved;
            }
        }
        return null;
    }

    public static boolean isRuntimePresent(ClassLoader... preferredLoaders) {
        for (ClassLoader loader : candidateLoaders(preferredLoaders)) {
            Class<?> minecraftClass = resolveRuntimeClass(
                    MINECRAFT_CLASS, loader);
            if (minecraftClass == null) {
                continue;
            }
            ClassLoader definingLoader = minecraftClass.getClassLoader();
            if (definingLoader == null) {
                continue;
            }
            Class<?> clientLevel = resolveRuntimeClass(
                    CLIENT_LEVEL_CLASS, definingLoader);
            Class<?> localPlayer = resolveRuntimeClass(
                    LOCAL_PLAYER_CLASS, definingLoader);
            Class<?> abstractPlayer = resolveRuntimeClass(
                    ABSTRACT_PLAYER_CLASS, definingLoader);
            Class<?> level = resolveRuntimeClass(LEVEL_CLASS, definingLoader);
            if (clientLevel == null || localPlayer == null
                    || abstractPlayer == null || level == null
                    || clientLevel.getSuperclass() != level
                    || localPlayer.getSuperclass() != abstractPlayer) {
                continue;
            }
            if (matchesMinecraftStructure(minecraftClass)
                    && matchesVersionMetadata(minecraftClass)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesMinecraftStructure(Class<?> minecraftClass) {
        try {
            Method getter = minecraftClass.getDeclaredMethod("getInstance");
            Field instance = minecraftClass.getDeclaredField("instance");
            return Modifier.isStatic(getter.getModifiers())
                    && getter.getReturnType() == minecraftClass
                    && Modifier.isStatic(instance.getModifiers())
                    && instance.getType() == minecraftClass;
        }
        catch (ReflectiveOperationException ignored) {
            return false;
        }
        catch (SecurityException ignored) {
            return false;
        }
        catch (LinkageError ignored) {
            return false;
        }
    }

    private static boolean matchesVersionMetadata(Class<?> minecraftClass) {
        try (InputStream stream = minecraftClass.getResourceAsStream(
                "/version.json")) {
            if (stream == null) {
                return false;
            }
            JsonElement element = JsonParser.parseReader(new InputStreamReader(
                    stream, StandardCharsets.UTF_8));
            if (!element.isJsonObject()) {
                return false;
            }
            JsonObject version = element.getAsJsonObject();
            return "26.2".equals(getString(version, "id"))
                    && "26.2".equals(getString(version, "name"))
                    && getInteger(version, "world_version") == 4903
                    && getInteger(version, "protocol_version") == 776
                    && getInteger(version, "java_version") == 25;
        }
        catch (Exception ignored) {
            return false;
        }
        catch (LinkageError ignored) {
            return false;
        }
    }

    private static String getString(JsonObject object, String name) {
        JsonElement value = object.get(name);
        return value != null && value.isJsonPrimitive()
                ? value.getAsString() : null;
    }

    private static int getInteger(JsonObject object, String name) {
        JsonElement value = object.get(name);
        return value != null && value.isJsonPrimitive()
                ? value.getAsInt() : Integer.MIN_VALUE;
    }

    private static Class<?> resolveRuntimeClass(
            String internalName, ClassLoader loader) {
        try {
            return Class.forName(internalName.replace('/', '.'), false, loader);
        }
        catch (ClassNotFoundException ignored) {
            return null;
        }
        catch (LinkageError ignored) {
            return null;
        }
        catch (SecurityException ignored) {
            return null;
        }
    }

    private static Set<ClassLoader> candidateLoaders(
            ClassLoader... preferredLoaders) {
        Set<ClassLoader> loaders = new LinkedHashSet<ClassLoader>();
        if (preferredLoaders != null) {
            for (ClassLoader loader : preferredLoaders) {
                if (loader != null) {
                    loaders.add(loader);
                }
            }
        }
        ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
        if (contextLoader != null) {
            loaders.add(contextLoader);
        }
        ClassLoader mappingLoader = Vanilla262Mappings.class.getClassLoader();
        if (mappingLoader != null) {
            loaders.add(mappingLoader);
        }
        try {
            ClassLoader systemLoader = ClassLoader.getSystemClassLoader();
            if (systemLoader != null) {
                loaders.add(systemLoader);
            }
        }
        catch (SecurityException ignored) {
            // The injected runtime loaders above are sufficient.
        }
        return loaders;
    }

    private static String normalizeInternalName(String className) {
        if (className == null) {
            return null;
        }
        String normalized = className.trim().replace('.', '/');
        if (normalized.length() > 2 && normalized.charAt(0) == 'L'
                && normalized.charAt(normalized.length() - 1) == ';') {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        return normalized;
    }
}
