/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.render.blockesp;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceArrayList;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.BlockUpdateEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.render.color.RainbowColors;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.Dimension;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.chunk.Chunk;

import java.util.*;

public class BlockESP extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    // General

    private final Setting<List<Block>> blocks = sgGeneral.add(new BlockListSetting.Builder()
        .name("blocks")
        .description("Blocks to search for.")
        .onChanged(blocks1 -> {
            if (isActive() && Utils.canUpdate()) onActivate();
        })
        .build()
    );

    private final Setting<ESPBlockData> defaultBlockConfig = sgGeneral.add(new GenericSetting.Builder<ESPBlockData>()
        .name("default-block-config")
        .description("Default block config.")
        .defaultValue(
            new ESPBlockData(
                ShapeMode.Lines,
                new SettingColor(0, 255, 200),
                new SettingColor(0, 255, 200, 25),
                true,
                new SettingColor(0, 255, 200, 125)
            )
        )
        .build()
    );

    private final Setting<Map<Block, ESPBlockData>> blockConfigs = sgGeneral.add(new BlockDataSetting.Builder<ESPBlockData>()
        .name("block-configs")
        .description("Config for each block.")
        .defaultData(defaultBlockConfig)
        .build()
    );

    private final Setting<Boolean> tracers = sgGeneral.add(new BoolSetting.Builder()
        .name("tracers")
        .description("Render tracer lines.")
        .defaultValue(false)
        .build()
    );

    private final Long2ObjectMap<ESPChunk> chunks = new Long2ObjectOpenHashMap<>();
    private final Set<ESPGroup> groups = new ReferenceOpenHashSet<>();

    private long chunkCount = 0;
    private double rollingAverage = 0d;

    private Dimension lastDimension;

    public BlockESP() {
        super(Categories.Render, "block-esp", "Renders specified blocks through walls.");

        RainbowColors.register(this::onTickRainbow);
    }

    @Override
    public void onActivate() {
        chunks.clear();
        groups.clear();

        for (Chunk chunk : Utils.chunks()) {
            searchChunk(chunk);
        }

        lastDimension = PlayerUtils.getDimension();
    }

    @Override
    public void onDeactivate() {
        chunks.clear();
        groups.clear();
    }

    private void onTickRainbow() {
        if (!isActive()) return;

        defaultBlockConfig.get().tickRainbow();
        for (ESPBlockData blockData : blockConfigs.get().values()) blockData.tickRainbow();
    }

    ESPBlockData getBlockData(Block block) {
        ESPBlockData blockData = blockConfigs.get().get(block);
        return blockData == null ? defaultBlockConfig.get() : blockData;
    }

    private void updateChunk(int x, int z, Direction direction, int side) {
        ESPChunk chunk = chunks.get(ChunkPos.toLong(x, z));
        if (chunk != null) chunk.update(direction, side);
    }

    private void updateBlock(int x, int y, int z) {
        ESPChunk chunk = chunks.get(ChunkPos.toLong(x >> 4, z >> 4));
        if (chunk != null) chunk.update(x, y, z);
    }

    public ESPBlock getBlock(int x, int y, int z) {
        ESPChunk chunk = chunks.get(ChunkPos.toLong(x >> 4, z >> 4));
        return chunk == null ? null : chunk.get(x, y, z);
    }

    public ESPGroup newGroup(Block block) {
        ESPGroup group = new ESPGroup(block);
        groups.add(group);
        return group;
    }

    public void removeGroup(ESPGroup group) {
        groups.remove(group);
    }

    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        searchChunk(event.chunk());
    }

    private void searchChunk(Chunk chunk) {
        long timer = System.currentTimeMillis();
        List<ESPGroup> returnedGroups = new ReferenceArrayList<>();
        ESPChunk schunk = ESPChunk.searchChunk(chunk, returnedGroups, blocks.get());

        if (!schunk.isEmpty()) {
            chunks.put(chunk.getPos().toLong(), schunk);
            groups.addAll(returnedGroups);

            // Update neighbour chunks
            updateChunk(chunk.getPos().x - 1, chunk.getPos().z, Direction.WEST, ESPBlock.RI);
            updateChunk(chunk.getPos().x + 1, chunk.getPos().z, Direction.EAST, ESPBlock.LE);
            updateChunk(chunk.getPos().x, chunk.getPos().z - 1, Direction.NORTH, ESPBlock.FO);
            updateChunk(chunk.getPos().x, chunk.getPos().z + 1, Direction.SOUTH, ESPBlock.BA);
        }
        timer = System.currentTimeMillis() - timer;
        rollingAverage = rollingAverage + ((timer - rollingAverage) / ++chunkCount);
    }

    @EventHandler
    private void onBlockUpdate(BlockUpdateEvent event) {
        // Minecraft probably reuses the event.pos BlockPos instance because it causes problems when trying to use it inside another thread
        int bx = event.pos.getX();
        int by = event.pos.getY();
        int bz = event.pos.getZ();

        int chunkX = bx >> 4;
        int chunkZ = bz >> 4;
        long key = ChunkPos.toLong(chunkX, chunkZ);

        boolean added = blocks.get().contains(event.newState.getBlock()) && !blocks.get().contains(event.oldState.getBlock());
        boolean removed = !added && !blocks.get().contains(event.newState.getBlock()) && blocks.get().contains(event.oldState.getBlock());

        if (added || removed) {
            ESPChunk chunk = chunks.get(key);

            if (chunk == null) {
                chunk = new ESPChunk(mc.world.getChunk(chunkX, chunkZ), chunkX, chunkZ);
                if (chunk.shouldBeDeleted()) return;

                chunks.put(key, chunk);
            }

            if (added) chunk.add(bx, by, bz);
            else chunk.remove(bx, by, bz);

            // Update neighbour blocks
            for (int x = -1; x < 2; x++) {
                for (int z = -1; z < 2; z++) {
                    for (int y = -1; y < 2; y++) {
                        if (x == 0 && y == 0 && z == 0) continue; // skip middle
                        if (((~x | ~y | ~z) & 1) == 0) continue; // skip corner diagonals

                        updateBlock(bx + x, by + y, bz + z);
                    }
                }
            }
        }
    }

    @EventHandler
    private void onPostTick(TickEvent.Post event) {
        Dimension dimension = PlayerUtils.getDimension();

        if (lastDimension != dimension) onActivate();

        lastDimension = dimension;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        for (Iterator<ESPChunk> it = chunks.values().iterator(); it.hasNext();) {
            ESPChunk chunk = it.next();

            if (chunk.shouldBeDeleted()) {
                for (ESPBlock block : chunk.blocks.values()) {
                    block.group.remove(block, false);
                    block.loaded = false;
                }

                it.remove();
            }
            else chunk.render(event);
        }

        if (tracers.get()) {
            for (ESPGroup group : groups) {
                group.render(event);
            }
        }
    }

    @Override
    public String getInfoString() {
        return "G: %s; C: %s; B: %s, Avg: %.2fms".formatted(
            groups.size(),
            chunks.size(),
            chunks.values().stream().mapToInt(chunk -> chunk.blocks.size()).sum(),
            rollingAverage
        );
    }
}
