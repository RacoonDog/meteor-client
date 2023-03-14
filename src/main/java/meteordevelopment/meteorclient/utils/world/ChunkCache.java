/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.utils.world;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.chunk.Chunk;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * Local cache for {@link Chunk} objects centered around a certain coordinate, to lower the cost of repeated semi-random {@link net.minecraft.world.World#getBlockState(BlockPos)} calls.
 * @author Crosby
 */
public class ChunkCache {
    private final Chunk[] cache;
    private final int minX;
    private final int minZ;
    private final int length;

    public ChunkCache(int minX, int minZ, int length) {
        this.cache = new Chunk[length * length];
        this.minX = minX;
        this.minZ = minZ;
        this.length = length;
    }

    /**
     * @return a {@link ChunkCache} centered around the player.
     */
    public static ChunkCache create() {
        return ofCoord(mc.player.getBlockX(), mc.player.getBlockZ(), 2);
    }

    /**
     * @param length Distance between the center and the edge of the cache in chunks.
     */
    public static ChunkCache ofCoord(int x, int z, int length) {
        return ofSectionCoord(ChunkSectionPos.getSectionCoord(x), ChunkSectionPos.getSectionCoord(z), length);
    }

    public static ChunkCache ofSectionCoord(int x, int z, int length) {
        return new ChunkCache(x - length, z - length, length * 2 + 1);
    }

    public Chunk getSection(int sectionX, int sectionZ) {
        int index = sectionX - minX + (sectionZ - minZ) * length;
        if (index < 0 || index >= cache.length) return mc.world.getChunk(sectionX, sectionZ);
        Chunk c = cache[index];
        if (c == null) {
            c = mc.world.getChunk(sectionX, sectionZ);
            cache[index] = c;
        }
        return c;
    }

    public Chunk get(int x, int z) {
        return getSection(ChunkSectionPos.getSectionCoord(x), ChunkSectionPos.getSectionCoord(z));
    }

    public Chunk get(Vec3i vec) {
        return get(vec.getX(), vec.getZ());
    }
}
