/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.commands.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.mixin.MapRendererAccessor;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.render.MapRenderer;
import net.minecraft.command.CommandSource;
import net.minecraft.item.FilledMapItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.map.MapState;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.BufferUtils;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;
import static meteordevelopment.meteorclient.MeteorClient.mc;

public class SaveMapCommand extends Command {
    private static final SimpleCommandExceptionType MAP_NOT_FOUND = new SimpleCommandExceptionType(Text.literal("You must be holding a filled map."));
    private static final SimpleCommandExceptionType OOPS = new SimpleCommandExceptionType(Text.literal("Something went wrong."));

    private final PointerBuffer filters;

    public SaveMapCommand() {
        super("save-map", "Saves a map to an image.", "sm");

        filters = BufferUtils.createPointerBuffer(1);

        ByteBuffer pngFilter = MemoryUtil.memASCII("*.png");

        filters.put(pngFilter);
        filters.rewind();
    }

    @Override
    public void build(LiteralArgumentBuilder<FabricClientCommandSource> builder) {
        builder.executes(context -> {
            Integer id = getMapId();
            MapState state = FilledMapItem.getMapState(id, mc.world);
            String path = getPath();

            saveMap(id, state, path);

            return SINGLE_SUCCESS;
        });
    }

    private void saveMap(Integer id, MapState state, String path) throws CommandSyntaxException {
        try {
            MapRenderer mapRenderer = mc.gameRenderer.getMapRenderer();
            MapRenderer.MapTexture texture = ((MapRendererAccessor) mapRenderer).invokeGetMapTexture(id, state);
            texture.texture.getImage().writeTo(new File(path));
        } catch (IOException e) {
            e.printStackTrace();
            throw OOPS.create();
        }
    }

    private Integer getMapId() throws CommandSyntaxException {
        ItemStack map = getMap();
        if (map == null) throw MAP_NOT_FOUND.create();
        return FilledMapItem.getMapId(map);
    }

    private String getPath() throws CommandSyntaxException {
        String path = TinyFileDialogs.tinyfd_saveFileDialog("Save image", null, filters, null);
        if (path == null) throw OOPS.create();
        if (!path.endsWith(".png")) path += ".png";

        return path;
    }

    private ItemStack getMap() {
        ItemStack itemStack = mc.player.getMainHandStack();
        if (itemStack.getItem() == Items.FILLED_MAP) return itemStack;

        itemStack = mc.player.getOffHandStack();
        if (itemStack.getItem() == Items.FILLED_MAP) return itemStack;

        return null;
    }
}
