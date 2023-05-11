/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.utils.world;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.utils.PreInit;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.misc.Pool;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class BlockIterator {
    private static final Pool<Callback> callbackPool = new Pool<>(Callback::new);
    private static final List<Callback> callbacks = new ArrayList<>();

    private static final List<Runnable> afterCallbacks = new ArrayList<>();

    private static final BlockPos.Mutable blockPos = new BlockPos.Mutable();
    private static int hRadius, vRadius;
    private static int px, py, pz;

    private static boolean disableCurrent;

    @PreInit
    public static void init() {
        MeteorClient.EVENT_BUS.subscribe(BlockIterator.class);
    }

    @EventHandler(priority = EventPriority.LOWEST - 1)
    private static void onTick(TickEvent.Pre event) {
        if (!Utils.canUpdate() || mc.world.isDebugWorld() || hRadius == 0 || vRadius == 0) return;

        px = mc.player.getBlockX();
        py = mc.player.getBlockY();
        pz = mc.player.getBlockZ();

        int x1 = px - hRadius;
        int y1 = py - vRadius;
        int z1 = pz - hRadius;
        int x2 = px + hRadius;
        int y2 = py + vRadius;
        int z2 = pz + hRadius;

        y1 = Math.max(y1, Math.min(mc.world.getBottomY(), y2));
        y2 = Math.max(y2, Math.min(y1, mc.world.getTopY()));

        int cx1 = x1 >> 4;
        int cy1 = y1 >> 4;
        int cz1 = z1 >> 4;
        int cx2 = x2 >> 4;
        int cy2 = y2 >> 4;
        int cz2 = z2 >> 4;

        if (cx1 == cx2 && cz1 == cz2) {
            if (cy1 == cy2) sectionIterator(cx1, cy1, cz1, x1, y1, z1, x2, y2, z2);
            else chunkIterator(cx1, cz1, cy1, cy2, x1, y1, z1, x2, y2, z2);
        } else regionIterator(cx1, cy1, cz1, cx2, cy2, cz2, x1, y1, z1, x2, y2, z2);

        hRadius = 0;
        vRadius = 0;

        for (Callback callback : callbacks) callbackPool.free(callback);
        callbacks.clear();

        for (Runnable callback : afterCallbacks) callback.run();
        afterCallbacks.clear();
    }

    private static void regionIterator(int cx1, int cy1, int cz1, int cx2, int cy2, int cz2, int x1, int y1, int z1, int x2, int y2, int z2) {
        for (int cx = cx1; cx <= cx2; cx++) {
            int chunkEdgeX = cx << 4;
            int chunkX1 = Math.max(chunkEdgeX, x1);
            int chunkX2 = Math.min(chunkEdgeX + 15, x2);

            for (int cz = cz1; cz <= cz2; cz++) {
                int chunkEdgeZ = cz << 4;
                int chunkZ1 = Math.max(chunkEdgeZ, z1);
                int chunkZ2 = Math.min(chunkEdgeZ + 15, z2);

                if (cy1 == cy2) sectionIterator(cx, cy1, cz, chunkX1, y1, chunkZ1, chunkX2, y2, chunkZ2);
                chunkIterator(cx, cz, cy1, cy2, chunkX1, y1, chunkZ1, chunkX2, y2, chunkZ2);
            }
        }
    }

    private static void chunkIterator(int cx, int cz, int cy1, int cy2, int x1, int y1, int z1, int x2, int y2, int z2) {
        WorldChunk chunk = mc.world.getChunk(cx, cz);

        for (int cy = cy1; cy < cy2; cy++) {
            int chunkEdgeY = cy << 4;
            int chunkY1 = Math.max(chunkEdgeY, y1);
            int chunkY2 = Math.min(chunkEdgeY + 15, y2);

            sectionIterator(chunk, cy, x1, chunkY1, z1, x2, chunkY2, z2);
        }
    }

    private static void sectionIterator(int cx, int cy, int cz, int x1, int y1, int z1, int x2, int y2, int z2) {
        sectionIterator(mc.world.getChunk(cx, cz), cy, x1, y1, z1, x2, y2, z2);
    }

    private static void sectionIterator(WorldChunk chunk, int cy, int x1, int y1, int z1, int x2, int y2, int z2) {
        int sIndex = chunk.sectionCoordToIndex(cy);
        if (sIndex < 0 || sIndex >= chunk.getSectionArray().length) return;
        ChunkSection section = chunk.getSection(sIndex);
        if (section.isEmpty()) return;

        for (int y = y1; y <= y2; y++) {
            int ey = y & 15;
            blockPos.setY(y);
            int dy = Math.abs(y - py);

            for (int x = x1; x <= x2; x++) {
                int ex = x & 15;
                blockPos.setX(x);
                int dx = Math.abs(x - px);

                for (int z = z1; z <= z2; z++) {
                    BlockState blockState = section.getBlockState(ex, ey, z & 15);
                    blockPos.setZ(z);
                    int dz = Math.abs(z - pz);

                    if (callbacks(dx, dy, dz, blockState)) return;
                }
            }
        }
    }

    private static boolean callbacks(int dx, int dy, int dz, BlockState blockState) {
        for (int i = 0; i < callbacks.size(); i++) {
            Callback callback = callbacks.get(i);

            if (dy <= callback.vRadius && Math.max(dx, dz) <= callback.hRadius) {
                disableCurrent = false;
                callback.function.accept(blockPos, blockState);
                if (disableCurrent) {
                    callbacks.remove(i--);
                    if (callbacks.isEmpty()) return true;
                }
            }
        }

        return false;
    }

    public static void register(int horizontalRadius, int verticalRadius, BiConsumer<BlockPos, BlockState> function) {
        hRadius = Math.max(hRadius, horizontalRadius);
        vRadius = Math.max(vRadius, verticalRadius);

        Callback callback = callbackPool.get();

        callback.function = function;
        callback.hRadius = horizontalRadius;
        callback.vRadius = verticalRadius;

        callbacks.add(callback);
    }

    public static void disableCurrent() {
        disableCurrent = true;
    }

    public static void after(Runnable callback) {
        afterCallbacks.add(callback);
    }

    private static class Callback {
        public BiConsumer<BlockPos, BlockState> function;
        public int hRadius, vRadius;
    }
}
