/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.renderer.text;

import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import meteordevelopment.meteorclient.MeteorClient;
import net.fabricmc.loader.api.FabricLoader;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.nio.file.Path;
import java.util.List;

import static org.lwjgl.stb.STBTruetype.*;

/**
 * A minimal TrueType font file parser to efficiently decode font metadata.
 * <br><a href="https://learn.microsoft.com/en-us/typography/opentype/spec/otff">TTF specification</a>
 * <br><a href="https://developer.apple.com/fonts/TrueType-Reference-Manual/RM06/Chap6name.html">Name Table Entries</a>
 *
 * @author Crosby
 */
public class TTFMetadataParser {
    private static final int NAME_TAG = toTag("name");
    private static final int HEADER_SIZE = Integer.BYTES + Short.BYTES * 4;
    private static final int DIRECTORY_ENTRY_SIZE = Integer.BYTES * 4;

    @Nullable
    public static FontInfo readFile(Path file) {
        try (FileChannel channel = FileChannel.open(file)) {
            // read header
            ByteBuffer headerBuffer = readBuffer(channel, Integer.BYTES + Short.BYTES);

            long sfntVersion = Integer.toUnsignedLong(headerBuffer.getInt());
            if (sfntVersion != 0x00010000) {
                MeteorClient.LOG.debug("Discarding font {}, invalid version {}", file.getFileName().toString(), sfntVersion);
                return null;
            }

            int numTables = Short.toUnsignedInt(headerBuffer.getShort());
            channel.position(HEADER_SIZE);

            // read directory
            ByteBuffer directoryBuffer = readBuffer(channel, DIRECTORY_ENTRY_SIZE * numTables);

            int nameTableOffset = -1;
            int nameTableLength = -1;

            for (int i = 0; i < numTables; i++) {
                int tag = directoryBuffer.getInt();

                if (tag == NAME_TAG) {
                    directoryBuffer.position(directoryBuffer.position() + Integer.BYTES); // skip checksum
                    nameTableOffset = (int) Integer.toUnsignedLong(directoryBuffer.getInt());
                    nameTableLength = (int) Integer.toUnsignedLong(directoryBuffer.getInt());
                    break;
                } else {
                    directoryBuffer.position(directoryBuffer.position() + Integer.BYTES * 3);
                }
            }

            if (nameTableOffset == -1) {
                MeteorClient.LOG.debug("Discarding font {}, could not find name table", file.getFileName().toString());
                return null;
            }

            channel.position(nameTableOffset);

            // read name table
            ByteBuffer nameTableBuffer = readBuffer(channel, nameTableLength);

            nameTableBuffer.position(Short.BYTES); // skip version, they're backwards compatible
            int count = Short.toUnsignedInt(nameTableBuffer.getShort());
            int storageOffset = Short.toUnsignedInt(nameTableBuffer.getShort());

            String fontName = null;
            String fontType = null;

            for (int i = 0; i < count; i++) {
                int platformID = Short.toUnsignedInt(nameTableBuffer.getShort());
                int encodingID = Short.toUnsignedInt(nameTableBuffer.getShort());
                int languageID = Short.toUnsignedInt(nameTableBuffer.getShort());
                int nameID = Short.toUnsignedInt(nameTableBuffer.getShort());

                // todo we can fallback to others if not present
                if (platformID == STBTT_PLATFORM_ID_MICROSOFT && encodingID == STBTT_MS_EID_UNICODE_BMP && languageID == STBTT_MS_LANG_ENGLISH && nameID == 1) {
                    int fontNameLength = Short.toUnsignedInt(nameTableBuffer.getShort());
                    int fontNameOffset = Short.toUnsignedInt(nameTableBuffer.getShort());
                    fontName = readString(nameTableBuffer, storageOffset + fontNameOffset, fontNameLength);
                    if (fontType != null) break;
                } else if (platformID == STBTT_PLATFORM_ID_MICROSOFT && encodingID == STBTT_MS_EID_UNICODE_BMP && languageID == STBTT_MS_LANG_ENGLISH && nameID == 2) {
                    int fontTypeLength = Short.toUnsignedInt(nameTableBuffer.getShort());
                    int fontTypeOffset = Short.toUnsignedInt(nameTableBuffer.getShort());
                    fontType = readString(nameTableBuffer, storageOffset + fontTypeOffset, fontTypeLength);
                    if (fontName != null) break;
                } else {
                    nameTableBuffer.position(nameTableBuffer.position() + Short.BYTES * 2);
                }
            }

            boolean invalid = fontName == null || fontType == null;

            if (DEBUG_ALL_FONTS || (DEBUG && invalid)) {
                channel.position(nameTableOffset);
                nameTableBuffer = readBuffer(channel, nameTableLength);
                nameTableBuffer.position(Short.BYTES * 3);
                Debug.debug(file, nameTableBuffer, count, storageOffset);
            }

            // no suitable name entries found
            if (invalid) {
                String fileName = file.getFileName().toString();
                MeteorClient.LOG.debug("No suitable name entries found for font {}.", fileName);
                return null;
            }

            return new FontInfo(fontName, FontInfo.Type.fromString(fontType));
        } catch (IOException e) {
            MeteorClient.LOG.debug("Discarding font %s, IOException".formatted(file.getFileName().toString()), e);
            return null;
        }
    }

    private static ByteBuffer readBuffer(FileChannel channel, int length) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(length);
        buffer.order(ByteOrder.BIG_ENDIAN);
        channel.read(buffer);
        return buffer.flip();
    }

    private static String readString(ByteBuffer buffer, int offset, int length) {
        return StandardCharsets.UTF_16BE.decode(buffer.slice(offset, length)).toString();
    }

    /**
     * TTF tables are identified using "tags" which are short four character identifiers, that can also be efficiently
     * represented as integers.
     *
     * @author Crosby
     */
    private static int toTag(String string) {
        if (string.length() != 4) {
            throw new IllegalArgumentException("TTF Tag must be exactly 4 characters.");
        }

        int value = 0;
        value |= string.charAt(0) << 24;
        value |= string.charAt(1) << 16;
        value |= string.charAt(2) << 8;
        value |= string.charAt(3);
        return value;
    }

    /**
     * Enables font debugging on erroring fonts.
     */
    private static final boolean DEBUG = FabricLoader.getInstance().isDevelopmentEnvironment() || Boolean.getBoolean("meteor.font.debug");

    /**
     * Enables font debugging for all fonts.
     */
    private static final boolean DEBUG_ALL_FONTS = Boolean.getBoolean("meteor.font.debug");

    private static class Debug {
        private static final @Nullable Charset MAC_ROMAN_CHARSET;
        private static final Int2ObjectMap<String> PLATFORM_IDS_MAP = new Int2ObjectArrayMap<>(4) {{
            put(0, "Unicode");
            put(1, "Mac");
            put(2, "Iso");
            put(3, "Microsoft");
        }};

        private static final Int2ObjectMap<String> NAME_IDS_MAP = new Int2ObjectArrayMap<>(4) {{
            put(1, "Family");
            put(2, "Subfamily");
            put(3, "Unique Id");
            put(4, "Full Name");
            put(6, "PostScript Name");
        }};

        static {
            Charset c;
            try {
                c = Charset.forName("MacRoman");
            } catch (UnsupportedCharsetException e) {
                c = null;
            }
            MAC_ROMAN_CHARSET = c;
        }

        @Nullable
        private static String readStringMacRoman(ByteBuffer buffer, int offset, int length) {
            return MAC_ROMAN_CHARSET != null ? MAC_ROMAN_CHARSET.decode(buffer.slice(offset, length)).toString() : null;
        }

        private static void debug(Path file, ByteBuffer nameTableBuffer, int count, int storageOffset) {
            StringBuilder output = new StringBuilder(String.format("Debugging font %s\n", file.getFileName()));

            @Nullable String nameCandidate = null;
            @Nullable String typeCandidate = null;
            List<Entry> entries = new ObjectArrayList<>();

            for (int i = 0; i < count; i++) {
                int platformID = Short.toUnsignedInt(nameTableBuffer.getShort());
                int encodingID = Short.toUnsignedInt(nameTableBuffer.getShort());
                int languageID = Short.toUnsignedInt(nameTableBuffer.getShort());
                int nameID = Short.toUnsignedInt(nameTableBuffer.getShort());
                int length = Short.toUnsignedInt(nameTableBuffer.getShort());
                int offset = Short.toUnsignedInt(nameTableBuffer.getShort());

                @Nullable String nameType = NAME_IDS_MAP.get(nameID);
                if (nameType == null || length > 64) continue;

                boolean macRoman = platformID == STBTT_PLATFORM_ID_MAC && encodingID == STBTT_MAC_EID_ROMAN;
                @Nullable String str = macRoman
                    ? readStringMacRoman(nameTableBuffer, storageOffset + offset, length)
                    : readString(nameTableBuffer, storageOffset + offset, length);
                if (str == null) continue;

                @Nullable String platform = PLATFORM_IDS_MAP.get(platformID);
                if (platform == null) platform = String.format("Unknown (%s)", platformID);

                entries.add(new Entry(platform, encodingID, languageID, nameType, str));

                if (platformID == STBTT_PLATFORM_ID_MICROSOFT && encodingID == STBTT_MS_EID_UNICODE_BMP && languageID == STBTT_MS_LANG_ENGLISH && nameID == 1) {
                    nameCandidate = str;
                } else if (platformID == STBTT_PLATFORM_ID_MICROSOFT && encodingID == STBTT_MS_EID_UNICODE_BMP && languageID == STBTT_MS_LANG_ENGLISH && nameID == 2) {
                    typeCandidate = str;
                }
            }

            if (entries.isEmpty()) {
                output.append("No valid NAME table entries.");
            } else {
                int w1 = Math.max(entries.stream().map(Entry::platform).mapToInt(String::length).max().orElseThrow(), "Platform".length());
                int w2 = "Encoding ID".length();
                int w3 = "Language ID".length();
                int w4 = Math.max(entries.stream().map(Entry::nameType).mapToInt(String::length).max().orElseThrow(), "Name Type".length());
                int w5 = Math.max(entries.stream().map(Entry::string).mapToInt(String::length).max().orElseThrow(), "String".length());

                String titleFormatString = "|%-" + w1 + "s|%-" + w2 + "s|%-" + w3 + "s|%-" + w4 + "s|%-" + w5 + "s|\n";
                String entryFormatString = "|%" + w1 + "s|%" + w2 + "d|%" + w3 + "d|%" + w4 + "s|%" + w5 + "s|\n";
                String separatorString = String.format("+%s+%s+%s+%s+%s+\n", "-".repeat(w1), "-".repeat(w2), "-".repeat(w3), "-".repeat(w4), "-".repeat(w5));

                output.append(separatorString);
                output.append(String.format(titleFormatString, "Platform", "Encoding ID", "Language ID", "Name Type", "String"));
                output.append(separatorString);

                for (Entry entry : entries) {
                    output.append(String.format(entryFormatString, entry.platform(), entry.encodingId(), entry.languageId(), entry.nameType(), entry.string()));
                }

                output.append(separatorString);

                if (nameCandidate == null && typeCandidate == null) {
                    output.append("No valid name & type candidates found.");
                } else if (nameCandidate == null) {
                    output.append("No valid name candidate found.");
                } else if (typeCandidate == null) {
                    output.append("No valid type candidate found.");
                }
            }

            MeteorClient.LOG.info(output.toString());
        }

        private record Entry(String platform, int encodingId, int languageId, String nameType, String string) {}
    }
}
