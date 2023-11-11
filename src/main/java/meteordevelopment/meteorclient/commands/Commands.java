/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import meteordevelopment.meteorclient.pathing.PathManagers;
import meteordevelopment.meteorclient.utils.PostInit;
import meteordevelopment.meteorclient.utils.ReflectInit;
import net.minecraft.client.network.ClientCommandSource;
import net.minecraft.command.CommandSource;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class Commands {
    public static final CommandDispatcher<CommandSource> DISPATCHER = new CommandDispatcher<>();
    public static final CommandSource COMMAND_SOURCE = new ClientCommandSource(null, mc);
    public static final List<Command> COMMANDS = new ArrayList<>();

    @PostInit(dependencies = PathManagers.class)
    public static void init() {
        ReflectInit.initRegisterable(Command.class, Commands::add);

        COMMANDS.sort(Comparator.comparing(Command::getName));
    }

    public static void add(Command command) {
        COMMANDS.removeIf(existing -> existing.getName().equals(command.getName()));
        command.registerTo(DISPATCHER);
        COMMANDS.add(command);
    }

    public static void dispatch(String message) throws CommandSyntaxException {
        DISPATCHER.execute(message, COMMAND_SOURCE);
    }

    public static Command get(String name) {
        for (Command command : COMMANDS) {
            if (command.getName().equals(name)) {
                return command;
            }
        }

        return null;
    }
}
