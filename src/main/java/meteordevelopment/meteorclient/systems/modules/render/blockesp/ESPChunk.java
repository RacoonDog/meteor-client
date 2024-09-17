/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.render.blockesp;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;

import java.util.ArrayList;
import java.util.List;

import static meteordevelopment.meteorclient.MeteorClient.mc;
import static meteordevelopment.meteorclient.utils.Utils.getRenderDistance;

public class ESPChunk {
    private final int x, z;
    public final Chunk chunk;
    public Long2ObjectMap<ESPBlock> blocks;

    public ESPChunk(Chunk chunk, int x, int z) {
        this.chunk = chunk;
        this.x = x;
        this.z = z;
    }

    public ESPBlock get(int x, int y, int z) {
        return blocks == null ? null : blocks.get(ESPBlock.getKey(x, y, z));
    }

    public ESPBlock add(int x, int y, int z) {
        ESPBlock block = new ESPBlock(this, x, y, z);

        if (blocks == null) blocks = new Long2ObjectOpenHashMap<>(64);
        blocks.put(ESPBlock.getKey(x, y, z), block);

        block.update(true);
        return block;
    }

    public void add(BlockPos blockPos) {
        add(blockPos.getX(), blockPos.getY(), blockPos.getZ());
    }

    public void remove(BlockPos blockPos) {
        if (blocks != null) {
            ESPBlock block = blocks.remove(ESPBlock.getKey(blockPos));
            if (block != null) block.group.remove(block);
        }
    }

    public void remove(int x, int y, int z) {
        if (blocks != null) {
            ESPBlock block = blocks.remove(ESPBlock.getKey(x, y, z));
            if (block != null) block.group.remove(block);
        }
    }

    public void update() {
        if (blocks != null) {
            for (ESPBlock block : blocks.values()) block.update(true);
        }
    }

    public void update(Direction direction, int side) {
        if (blocks != null) {
            for (ESPBlock block : blocks.values()) {
                if (ChunkSectionPos.getSectionCoord(block.x) != ChunkSectionPos.getSectionCoord(block.x + direction.getOffsetX())
                || ChunkSectionPos.getSectionCoord(block.z) != ChunkSectionPos.getSectionCoord(block.z + direction.getOffsetZ())) {
                    block.update(true);
                    block.tryMerge(side);
                }
            }
        }
    }

    public void update(int x, int y, int z) {
        if (blocks != null) {
            ESPBlock block = blocks.get(ESPBlock.getKey(x, y, z));
            if (block != null) block.update(true);
        }
    }

    public int size() {
        return blocks == null ? 0 : blocks.size();
    }

    public boolean isEmpty() {
        return size() <= 0;
    }

    public boolean shouldBeDeleted() {
        int viewDist = getRenderDistance() + 1;
        int chunkX = ChunkSectionPos.getSectionCoord(mc.player.getBlockPos().getX());
        int chunkZ = ChunkSectionPos.getSectionCoord(mc.player.getBlockPos().getZ());

        return x > chunkX + viewDist || x < chunkX - viewDist || z > chunkZ + viewDist || z < chunkZ - viewDist;
    }

    public void render(Render3DEvent event) {
        if (blocks != null) {
            for (ESPBlock block : blocks.values()) block.render(event);
        }
    }

    public static ESPChunk searchChunk(Chunk chunk, List<ESPGroup> returnedGroups, List<Block> blocks) {
        ESPChunk schunk = new ESPChunk(chunk, chunk.getPos().x, chunk.getPos().z);
        if (schunk.shouldBeDeleted()) return schunk;

        List<ESPBlock> scannedBlocks = new ArrayList<>();

        ChunkSection[] arr = chunk.getSectionArray();
        int minX = chunk.getPos().getStartX();
        int minZ = chunk.getPos().getStartZ();
        int[] paletteIndices = new int[16 * 16 * 16];
        for (int cy = 0; cy < arr.length; cy++) {
            ChunkSection section = arr[cy];
            if (!section.isEmpty() && section.getBlockStateContainer().hasAny(state -> blocks.contains(state.getBlock()))) {
                int minY = ChunkSectionPos.getBlockCoord(chunk.sectionIndexToCoord(cy));
                section.getBlockStateContainer().data.storage().writePaletteIndices(paletteIndices);

                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        for (int x = 0; x < 16; x++) {
                            int index = y * 16 * 16 + z * 16 + x;
                            BlockState state = section.getBlockStateContainer().data.palette().get(paletteIndices[index]);
                            if (blocks.contains(state.getBlock())) {
                                ESPBlock block = schunk.add(x + minX, y + minY, z + minZ);
                                scannedBlocks.add(block);
                            }
                        }
                    }
                }
            }
        }

        if (!schunk.isEmpty()) {
            for (ESPBlock block : scannedBlocks) {
                block.deferredAssignGroup(returnedGroups);
            }
        }

        return schunk;
    }
}
