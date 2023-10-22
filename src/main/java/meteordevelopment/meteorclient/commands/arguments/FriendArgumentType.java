/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.commands.arguments;

import com.google.common.collect.Streams;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import meteordevelopment.meteorclient.systems.friends.Friend;
import meteordevelopment.meteorclient.systems.friends.Friends;
import net.minecraft.text.Text;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static net.minecraft.command.CommandSource.suggestMatching;

public class FriendArgumentType implements ArgumentType<Friend> {
    private static final DynamicCommandExceptionType NO_SUCH_FRIEND = new DynamicCommandExceptionType(name -> Text.literal("Friend with name " + name + " doesn't exist."));

    private static final Collection<String> EXAMPLES = List.of("seasnail8169", "MineGame159");

    public static FriendArgumentType create() {
        return new FriendArgumentType();
    }

    public static Friend get(CommandContext<?> context) {
        return context.getArgument("friend", Friend.class);
    }

    public static Friend get(CommandContext<?> context, String name) {
        return context.getArgument(name, Friend.class);
    }

    @Override
    public Friend parse(StringReader reader) throws CommandSyntaxException {
        String friendName = reader.readString();
        Friend friend = Friends.get().get(friendName);
        if (friend == null) throw NO_SUCH_FRIEND.create(friendName);
        return friend;
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        return suggestMatching(Streams.stream(Friends.get()).map(Friend::getName), builder);
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }
}
