/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.utils.files;

import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.util.crash.CrashException;
import org.apache.commons.io.FilenameUtils;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class StreamUtils {
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss", Locale.ROOT);

    public static void copy(File from, File to) {
        try {
            InputStream in = new FileInputStream(from);
            OutputStream out = new FileOutputStream(to);

            copy(in, out);

            in.close();
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void copy(InputStream in, File to) {
        try {
            OutputStream out = new FileOutputStream(to);

            copy(in, out);

            in.close();
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void copy(InputStream in, OutputStream out) {
        byte[] bytes = new byte[512];
        int read;

        try {
            while ((read = in.read(bytes)) != -1) out.write(bytes, 0, read);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void writeNbt(@Nullable File folder, File file, NbtCompound tag) {
        try {
            File tempFile = File.createTempFile(MeteorClient.MOD_ID, file.getName());
            NbtIo.write(tag, tempFile);

            if (folder != null) file = new File(folder, file.getName());

            file.getParentFile().mkdirs();
            StreamUtils.copy(tempFile, file);
            tempFile.delete();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Nullable
    public static NbtCompound readNbt(@Nullable File folder, File file) {
        try {
            if (folder != null) file = new File(folder, file.getName());

            if (file.exists()) {
                try {
                    return NbtIo.read(file);
                } catch (CrashException e) {
                    String backupName = FilenameUtils.removeExtension(file.getName()) + "-" + ZonedDateTime.now().format(DATE_TIME_FORMATTER) + ".backup.nbt";
                    File backup = new File(file.getParentFile(), backupName);
                    StreamUtils.copy(file, backup);
                    MeteorClient.LOG.error("Error loading " + file.getName() + ". Possibly corrupted?");
                    MeteorClient.LOG.info("Saved settings backup to '" + backup + "'.");
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}
