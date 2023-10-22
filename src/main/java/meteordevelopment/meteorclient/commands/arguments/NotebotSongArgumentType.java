/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.commands.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.utils.notebot.decoder.SongDecoders;
import net.minecraft.command.CommandSource;
import net.minecraft.text.Text;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public class NotebotSongArgumentType implements ArgumentType<Path> {
    private static final DynamicCommandExceptionType NO_SUCH_SONG = new DynamicCommandExceptionType(name -> Text.literal("Song with name " + name + " doesn't exist."));

    public static NotebotSongArgumentType create() {
        return new NotebotSongArgumentType();
    }

    public static Path get(CommandContext<?> context) {
        return context.getArgument("song", Path.class);
    }

    public static Path get(CommandContext<?> context, String name) {
        return context.getArgument(name, Path.class);
    }

    @Override
    public Path parse(StringReader reader) throws CommandSyntaxException {
        String argument = reader.readString();
        Path songPath = MeteorClient.FOLDER.toPath().resolve("notebot").resolve(argument);
        if (!Files.isRegularFile(songPath)) throw NO_SUCH_SONG.create(songPath.toString());
        return songPath;
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        try (var suggestions = Files.list(MeteorClient.FOLDER.toPath().resolve("notebot"))) {
            return CommandSource.suggestMatching(suggestions
                    .filter(SongDecoders::hasDecoder)
                    .map(path -> path.getFileName().toString()),
                builder
            );
        } catch (IOException e) {
            return Suggestions.empty();
        }
    }
}
