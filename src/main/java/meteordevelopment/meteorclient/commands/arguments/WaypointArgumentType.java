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
import meteordevelopment.meteorclient.systems.waypoints.Waypoint;
import meteordevelopment.meteorclient.systems.waypoints.Waypoints;
import net.minecraft.command.CommandSource;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class WaypointArgumentType implements ArgumentType<Waypoint> {
    private static final WaypointArgumentType INSTANCE = new WaypointArgumentType();
    private static final DynamicCommandExceptionType NO_SUCH_WAYPOINT = new DynamicCommandExceptionType(name -> Text.literal("Waypoint with name '" + name + "' doesn't exist."));

    private WaypointArgumentType() {}

    public static WaypointArgumentType create() {
        return INSTANCE;
    }

    public static <S> Waypoint get(CommandContext<S> context) {
        return context.getArgument("waypoint", Waypoint.class);
    }

    public static <S> Waypoint get(CommandContext<S> context, String name) {
        return context.getArgument(name, Waypoint.class);
    }

    @Override
    public Waypoint parse(StringReader reader) throws CommandSyntaxException {
        String argument = reader.readString();
        Waypoint waypoint = Waypoints.get().get(argument);
        if (waypoint == null) throw NO_SUCH_WAYPOINT.create(argument);

        return waypoint;
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        return CommandSource.suggestMatching(getExamples(), builder);
    }

    @Override
    public Collection<String> getExamples() {
        List<String> names = new ArrayList<>();
        for (Waypoint waypoint : Waypoints.get()) names.add(waypoint.name.get());
        return names;
    }
}

