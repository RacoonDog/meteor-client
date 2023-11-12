/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.render;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.Pool;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.Dimension;
import meteordevelopment.meteorclient.utils.world.Dir;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;

import java.util.ArrayList;
import java.util.List;

public class VoidESP extends Module {
    private static final Direction[] SIDES = {Direction.EAST, Direction.NORTH, Direction.SOUTH, Direction.WEST};

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    // General

    private final Setting<Boolean> airOnly = sgGeneral.add(new BoolSetting.Builder()
        .name("air-only")
        .description("Checks bedrock only for air blocks.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> horizontalRadius = sgGeneral.add(new IntSetting.Builder()
        .name("horizontal-radius")
        .description("Horizontal radius in which to search for holes.")
        .defaultValue(64)
        .min(0)
        .sliderMax(256)
        .build()
    );

    private final Setting<Integer> holeHeight = sgGeneral.add(new IntSetting.Builder()
        .name("hole-height")
        .description("The minimum hole height to be rendered.")
        .defaultValue(1)
        .min(1)
        .sliderRange(1, 5)
        .build()
    );

    private final Setting<Boolean> netherRoof = sgGeneral.add(new BoolSetting.Builder()
        .name("nether-roof")
        .description("Check for holes in nether roof.")
        .defaultValue(true)
        .build()
    );

    // Render

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How the shapes are rendered.")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("fill-color")
        .description("The color that fills holes in the void.")
        .defaultValue(new SettingColor(225, 25, 25, 50))
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color")
        .description("The color to draw lines of holes to the void.")
        .defaultValue(new SettingColor(225, 25, 255))
        .build()
    );

    private final BlockPos.Mutable blockPos = new BlockPos.Mutable();

    private final Pool<Void> voidHolePool = new Pool<>(Void::new);
    private final List<Void> voidHoles = new ArrayList<>();

    public VoidESP() {
        super(Categories.Render, "void-esp", "Renders holes in bedrock layers that lead to the void.");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        voidHoles.clear();
        if (PlayerUtils.getDimension() == Dimension.End) return;

        int px = mc.player.getBlockPos().getX();
        int pz = mc.player.getBlockPos().getZ();
        int radius = horizontalRadius.get();

        int x1 = px - radius;
        int z1 = pz - radius;
        int x2 = px + radius;
        int z2 = pz + radius;

        int cx1 = x1 >> 4;
        int cz1 = z1 >> 4;
        int cx2 = x2 >> 4;
        int cz2 = z2 >> 4;

        if (cx1 == cx2 && cz1 == cz2) {
            Chunk chunk = mc.world.getChunk(cx1, cz1, ChunkStatus.FULL, false);
            if (chunk == null) return;
            iterateChunk(chunk, x1, z1, x2, z2);
        } else {
            for (int cx = cx1; cx <= cx2; cx++) {
                int xEdge = cx << 4;
                int xMin = Math.max(xEdge, x1);
                int xMax = Math.min(xEdge + 15, x2);

                for (int cz = cz1; cz <= cz2; cz++) {
                    int zEdge = cz << 4;
                    int zMin = Math.max(zEdge, z1);
                    int zMax = Math.min(zEdge + 15, z2);

                    Chunk chunk = mc.world.getChunk(cx, cz, ChunkStatus.FULL, false);
                    if (chunk == null) continue;
                    iterateChunk(chunk, xMin, zMin, xMax, zMax);
                }
            }
        }
    }

    private void iterateChunk(Chunk chunk, int x1, int z1, int x2, int z2) {
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                blockPos.set(x, mc.world.getBottomY(), z);
                if (isHole(chunk, blockPos, false)) voidHoles.add(voidHolePool.get().set(chunk, blockPos, false));

                // Check for nether roof
                if (netherRoof.get() && PlayerUtils.getDimension() == Dimension.Nether) {
                    blockPos.set(x, 127, z);
                    if (isHole(chunk, blockPos, true)) voidHoles.add(voidHolePool.get().set(chunk ,blockPos.set(x, 127, z), true));
                }
            }
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        for (Void voidHole : voidHoles) voidHole.render(event);
    }

    private boolean isBlockWrong(BlockPos blockPos, Chunk chunk) {
        Block block = chunk.getBlockState(blockPos).getBlock();

        if (airOnly.get()) return block != Blocks.AIR;
        return block == Blocks.BEDROCK;
    }

    private boolean isHole(BlockPos.Mutable blockPos, boolean nether) {
        Chunk chunk = mc.world.getChunk(blockPos.getX() >> 4, blockPos.getZ() >> 4, ChunkStatus.FULL, false);
        if (chunk == null) return false;
        return isHole(chunk, blockPos, nether);
    }

    private boolean isHole(Chunk chunk, BlockPos.Mutable blockPos, boolean nether) {
        if (nether) {
            for (int y = 128 - holeHeight.get(); y <= 127; y++) {
                if (isBlockWrong(blockPos.setY(y), chunk)) return false;
            }
            return true;
        } else {
            return !isBlockWrong(blockPos.setY(mc.world.getBottomY()), chunk);
        }
    }

    private class Void {
        private static final int MASK = ~0xF;

        private int x, y, z;
        private int excludeDir;

        public Void set(Chunk chunk, BlockPos.Mutable blockPos, boolean nether) {
            x = blockPos.getX();
            y = blockPos.getY();
            z = blockPos.getZ();

            excludeDir = 0;

            int xChunk = x & MASK;
            int zChunk = z & MASK;

            for (Direction side : SIDES) {
                int offsetX = x + side.getOffsetX();
                int offsetZ = z + side.getOffsetZ();
                blockPos.set(offsetX, y, offsetZ);

                if ((offsetX & MASK) == xChunk && (offsetZ & MASK) == zChunk) {
                    if (isHole(chunk, blockPos, nether)) excludeDir |= Dir.get(side);
                } else {
                    if (isHole(blockPos, nether)) excludeDir |= Dir.get(side);
                }
            }

            return this;
        }

        public void render(Render3DEvent event) {
            event.renderer.box(x, y, z, x + 1, y + 1, z + 1, sideColor.get(), lineColor.get(), shapeMode.get(), excludeDir);
        }
    }
}
