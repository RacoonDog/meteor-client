/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.renderer.text;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.utils.Utils;
import org.apache.commons.io.FilenameUtils;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.lwjgl.stb.STBTruetype.*;

/**
 * A minimal TrueType font file parser to efficiently decode font metadata.
 * <a href="https://learn.microsoft.com/en-us/typography/opentype/spec/otff">TTF specification</a>
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

            // no suitable name entries found
            if (fontName == null || fontType == null) {
                String fileName = file.getFileName().toString();
                MeteorClient.LOG.debug("No suitable name entries found for font {}, using fallback", fileName);
                return new FontInfo(Utils.nameToTitle(FilenameUtils.removeExtension(fileName)), FontInfo.Type.Regular);
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
}
