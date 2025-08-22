/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.utils.render;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.renderer.Fonts;
import meteordevelopment.meteorclient.renderer.text.TTFMetadataParser;
import meteordevelopment.meteorclient.renderer.text.*;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.network.MeteorExecutor;
import net.minecraft.util.Util;
import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBTTFontinfo;
import org.lwjgl.stb.STBTruetype;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

public class FontUtils {
    private FontUtils() {
    }

    public static FontInfo getBuiltinFontInfo(String builtin) {
        return getFontInfo(stream(builtin));
    }

    public static FontInfo getFontInfo(InputStream stream) {
        if (stream == null) return null;

        byte[] bytes = Utils.readBytes(stream);
        if (bytes.length < 5) return null;

        if (
            bytes[0] != 0 ||
            bytes[1] != 1 ||
            bytes[2] != 0 ||
            bytes[3] != 0 ||
            bytes[4] != 0
        ) return null;

        ByteBuffer buffer = BufferUtils.createByteBuffer(bytes.length).put(bytes).flip();
        STBTTFontinfo fontInfo = STBTTFontinfo.create();
        if (!STBTruetype.stbtt_InitFont(fontInfo, buffer)) return null;

        ByteBuffer nameBuffer = STBTruetype.stbtt_GetFontNameString(fontInfo, STBTruetype.STBTT_PLATFORM_ID_MICROSOFT, STBTruetype.STBTT_MS_EID_UNICODE_BMP, STBTruetype.STBTT_MS_LANG_ENGLISH, 1);
        ByteBuffer typeBuffer = STBTruetype.stbtt_GetFontNameString(fontInfo, STBTruetype.STBTT_PLATFORM_ID_MICROSOFT, STBTruetype.STBTT_MS_EID_UNICODE_BMP, STBTruetype.STBTT_MS_LANG_ENGLISH, 2);
        if (typeBuffer == null || nameBuffer == null) return null;

        return new FontInfo(
            StandardCharsets.UTF_16.decode(nameBuffer).toString(),
            FontInfo.Type.fromString(StandardCharsets.UTF_16.decode(typeBuffer).toString())
        );
    }

    public static boolean isFontFile(Path path) {
        String fileName = path.getFileName().toString();
        return Files.isRegularFile(path) && (fileName.endsWith(".ttf") || fileName.endsWith(".otf"));
    }

    public static Set<Path> getSearchPaths() {
        Set<Path> paths = new ObjectOpenHashSet<>();
        paths.add(Paths.get(System.getProperty("java.home"), "libs", "fonts"));

        for (Path dir : getUFontDirs()) {
            if (Files.isDirectory(dir)) paths.add(dir.toAbsolutePath());
        }

        for (Path dir : getSFontDirs()) {
            if (Files.isDirectory(dir)) paths.add(dir.toAbsolutePath());
        }

        return paths;
    }

    public static List<Path> getUFontDirs() {
        return switch (Util.getOperatingSystem()) {
            case WINDOWS -> List.of(Path.of(System.getProperty("user.home"),  "AppData", "Local", "Microsoft", "Windows", "Fonts"));
            case OSX -> List.of(Path.of(System.getProperty("user.home"),  "Library", "Fonts"));
            default -> List.of(Path.of(System.getProperty("user.home"),  ".local", "share", "fonts"), Path.of(System.getProperty("user.home"),  ".fonts"));
        };
    }

    public static List<Path> getSFontDirs() {
        return switch (Util.getOperatingSystem()) {
            case WINDOWS -> List.of(Path.of(System.getenv("SystemRoot"), "Fonts"));
            case OSX -> List.of(Path.of("/System", "Library", "Fonts"));
            default -> List.of(Path.of("/usr", "share", "fonts"));
        };
    }

    public static void loadBuiltin(List<FontFamily> fontList, String builtin) {
        FontInfo fontInfo = FontUtils.getBuiltinFontInfo(builtin);
        if (fontInfo == null) return;

        FontFace fontFace = new BuiltinFontFace(fontInfo, builtin);
        if (!addFont(fontList, fontFace)) {
            MeteorClient.LOG.warn("Failed to load builtin font {}", fontFace);
        }
    }

    public static void loadSystem(List<FontFamily> fontList, List<CompletableFuture<Void>> futures, Path dir) {
        if (!Files.exists(dir)) return;

        try (Stream<Path> dirFiles = Files.list(dir)) {
            dirFiles.filter(file -> isFontFile(file) || Files.isDirectory(file))
                .forEach(file -> {
                    if (Files.isDirectory(file)) {
                        loadSystem(fontList, futures, file);
                        return;
                    }

                    futures.add(CompletableFuture.runAsync(() -> {
                        FontInfo fontInfo = TTFMetadataParser.readFile(file);
                        if (fontInfo == null) {
                            MeteorClient.LOG.warn("Failed to load system font {}", file.getFileName().toString());
                            return;
                        }

                        boolean isBuiltin = false;
                        for (String builtinFont : Fonts.BUILTIN_FONTS) {
                            if (builtinFont.equals(fontInfo.family())) {
                                isBuiltin = true;
                                break;
                            }
                        }
                        if (isBuiltin) return;

                        FontFace fontFace = new SystemFontFace(fontInfo, file);
                        if (!addFont(fontList, fontFace)) {
                            MeteorClient.LOG.warn("Failed to load system font {}", fontFace);
                        }
                    }, MeteorExecutor.executor));
                });
        } catch (IOException ignored) {}
    }

    @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
    public static boolean addFont(List<FontFamily> fontList, FontFace font) {
        if (font == null) return false;

        FontInfo info = font.info;

        FontFamily family;
        synchronized (fontList) {
            family = Fonts.getFamily(info.family());
            if (family == null) {
                family = new FontFamily(info.family());
                fontList.add(family);
            }
        }

        synchronized (family) {
            if (family.hasType(info.type())) return false;

            return family.addFont(font);
        }
    }

    public static InputStream stream(String builtin) {
        return FontUtils.class.getResourceAsStream("/assets/" + MeteorClient.MOD_ID + "/fonts/" + builtin + ".ttf");
    }
}
