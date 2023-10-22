/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.commands.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.commands.arguments.NotebotSongArgumentType;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.misc.Notebot;
import meteordevelopment.meteorclient.utils.notebot.song.Note;
import meteordevelopment.orbit.EventHandler;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.block.enums.Instrument;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

public class NotebotCommand extends Command {
    private static final DynamicCommandExceptionType INVALID_SONG_NAME = new DynamicCommandExceptionType(name -> Text.literal("Invalid song name " + name + "."));
    private static final DynamicCommandExceptionType COULD_NOT_CREATE_FILE = new DynamicCommandExceptionType(name -> Text.literal("Could not create file " + name + "."));

    int ticks = -1;
    private final Int2ObjectMap<List<Note>> song = new Int2ObjectOpenHashMap<>(); // tick -> notes

    public NotebotCommand() {
        super("notebot", "Allows you load notebot files");
    }

    @Override
    public void build(LiteralArgumentBuilder<FabricClientCommandSource> builder) {
        builder.then(literal("help").executes(ctx -> {
            Util.getOperatingSystem().open("https://github.com/MeteorDevelopment/meteor-client/wiki/Notebot-Guide");
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("status").executes(ctx -> {
            Notebot notebot = Modules.get().get(Notebot.class);
            info(notebot.getStatus());
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("pause").executes(ctx -> {
            Notebot notebot = Modules.get().get(Notebot.class);
            notebot.pause();
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("resume").executes(ctx -> {
            Notebot notebot = Modules.get().get(Notebot.class);
            notebot.pause();
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("stop").executes(ctx -> {
            Notebot notebot = Modules.get().get(Notebot.class);
            notebot.stop();
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("randomsong").executes(ctx -> {
            Notebot notebot = Modules.get().get(Notebot.class);
            notebot.playRandomSong();
            return SINGLE_SUCCESS;
        }));

        builder.then(
            literal("play").then(
                argument("song", NotebotSongArgumentType.create()).executes(ctx -> {
                    Notebot notebot = Modules.get().get(Notebot.class);
                    Path songPath = NotebotSongArgumentType.get(ctx);
                    notebot.loadSong(songPath.toFile());
                    return SINGLE_SUCCESS;
                })
            )
        );

        builder.then(
            literal("preview").then(
                argument("song", NotebotSongArgumentType.create()).executes(ctx -> {
                    Notebot notebot = Modules.get().get(Notebot.class);
                    Path songPath = NotebotSongArgumentType.get(ctx);
                    notebot.previewSong(songPath.toFile());
                    return SINGLE_SUCCESS;
        })));

        builder.then(literal("record").then(literal("start").executes(ctx -> {
            ticks = -1;
            song.clear();
            MeteorClient.EVENT_BUS.subscribe(this);
            info("Recording started");
            return SINGLE_SUCCESS;
        })));

        builder.then(literal("record").then(literal("cancel").executes(ctx -> {
            MeteorClient.EVENT_BUS.unsubscribe(this);
            info("Recording cancelled");
            return SINGLE_SUCCESS;
        })));

        builder.then(literal("record").then(literal("save").then(argument("name", StringArgumentType.greedyString()).executes(ctx -> {
            String name = StringArgumentType.getString(ctx, "name");
            if (name.isEmpty()) {
                throw INVALID_SONG_NAME.create(name);
            }
            Path path = MeteorClient.FOLDER.toPath().resolve(String.format("notebot/%s.txt", name));
            saveRecording(path);
            return SINGLE_SUCCESS;
        }))));
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (ticks == -1) return;
        ticks++;
    }

    @EventHandler
    private void onReadPacket(PacketEvent.Receive event) {
        if (event.packet instanceof PlaySoundS2CPacket sound && sound.getSound().value().getId().getPath().contains("note_block")) {
            if (ticks == -1) ticks = 0;
            List<Note> notes = song.computeIfAbsent(ticks, tick -> new ArrayList<>());
            var note = getNote(sound);
            if (note != null) {
                notes.add(note);
            }
        }
    }

    private void saveRecording(Path path) throws CommandSyntaxException {
        MeteorClient.EVENT_BUS.unsubscribe(this);
        if (song.isEmpty()) return;
        try {
            FileWriter file = new FileWriter(path.toFile());
            for (var entry : song.int2ObjectEntrySet()) {
                int tick = entry.getIntKey();
                List<Note> notes = entry.getValue();

                for (var note : notes) {
                    Instrument instrument = note.getInstrument();
                    int noteLevel = note.getNoteLevel();

                    file.write(String.format("%d:%d:%d\n", tick, noteLevel, instrument.ordinal()));
                }
            }

            file.close();
            info("Song saved.");
        } catch (IOException e) {
            throw COULD_NOT_CREATE_FILE.create(path);
        }
    }

    private Note getNote(PlaySoundS2CPacket soundPacket) {
        float pitch = soundPacket.getPitch();

        // Bruteforce note level
        int noteLevel = -1;
        for (int n = 0; n < 25; n++) {
            float computedPitch = (float) Math.pow(2.0D, (n - 12) / 12.0D);
            if (Math.abs(computedPitch - pitch) < 0.01f) {
                noteLevel = n;
                break;
            }
        }

        if (noteLevel == -1) {
            error("Error while bruteforcing a note level! Sound: " + soundPacket.getSound().value() + " Pitch: " + pitch);
            return null;
        }

        Instrument instrument = getInstrumentFromSound(soundPacket.getSound().value());
        if (instrument == null) {
            error("Can't find the instrument from sound! Sound: " + soundPacket.getSound().value());
            return null;
        }

        return new Note(instrument, noteLevel);
    }

    private Instrument getInstrumentFromSound(SoundEvent sound) {
        for (Instrument instrument : Instrument.values()) {
            if (instrument.getSound().value().getId().equals(sound.getId())) return instrument;
        }
        return null;
    }
}
