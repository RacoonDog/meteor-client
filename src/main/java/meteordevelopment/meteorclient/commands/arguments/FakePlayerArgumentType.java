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
import meteordevelopment.meteorclient.utils.entity.fakeplayer.FakePlayerEntity;
import meteordevelopment.meteorclient.utils.entity.fakeplayer.FakePlayerManager;
import net.minecraft.text.Text;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static net.minecraft.command.CommandSource.suggestMatching;

public class FakePlayerArgumentType implements ArgumentType<FakePlayerEntity> {
    private static final DynamicCommandExceptionType NO_SUCH_FAKE_PLAYER = new DynamicCommandExceptionType(name -> Text.literal("Fake Player with name " + name + " doesn't exist."));

    private static final Collection<String> EXAMPLES = List.of("seasnail8169", "MineGame159");

    public static FakePlayerArgumentType create() {
        return new FakePlayerArgumentType();
    }

    public static FakePlayerEntity get(CommandContext<?> context) {
        return context.getArgument("fp", FakePlayerEntity.class);
    }

    public static FakePlayerEntity get(CommandContext<?> context, String name) {
        return context.getArgument(name, FakePlayerEntity.class);
    }

    @Override
    public FakePlayerEntity parse(StringReader reader) throws CommandSyntaxException {
        String playerName = reader.readString();
        FakePlayerEntity fakePlayer = FakePlayerManager.get(playerName);
        if (fakePlayer == null) throw NO_SUCH_FAKE_PLAYER.create(playerName);
        return fakePlayer;
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        return suggestMatching(FakePlayerManager.stream().map(FakePlayerEntity::getEntityName), builder);
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }
}
