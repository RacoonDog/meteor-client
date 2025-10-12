/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.render.fabe;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import meteordevelopment.meteorclient.systems.modules.render.blockesp.ESPBlockData;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.block.Block;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;

import java.util.List;

public class FABEGroup {
    public final List<FABEBlock> blocks = new ObjectArrayList<>();
    public final Block block;
    public long x = 0L;
    public long y = 0L;
    public long z = 0L;
    public int count = 0;

    public FABEGroup(Block block) {
        this.block = block;
    }

    public void add(FABEBlock block, int x, int y, int z) {
        this.blocks.add(block);
        block.group = this;

        this.x += x;
        this.y += y;
        this.z += z;
        this.count++;
    }

    public void union(FABEGroup other, FABEChunk chunk) {
        for (FABEBlock block : other.blocks) {
            block.group = this;
        }

        this.blocks.addAll(other.blocks);
        this.x += other.x;
        this.y += other.y;
        this.z += other.z;
        this.count += other.count;

        chunk.remove(other);
    }

    public static void mesh(FABEMeshBuilder builder, ESPBlockData blockData, ChunkPos pos, List<FABEGroup> groups) {
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE;
        double maxX = Double.MIN_VALUE;
        double maxY = Double.MIN_VALUE;
        double maxZ = Double.MIN_VALUE;

        SettingColor lineColor = blockData.lineColor;
        SettingColor sideColor = blockData.sideColor;

        for (FABEGroup group : groups) {
            builder.tracerLine(
                blockData,
                pos.getStartX() + (double) group.x / group.count + 0.5d,
                (double) group.y / group.count + 0.5d,
                pos.getStartZ() + (double) group.z / group.count + 0.5d
            );

            for (FABEBlock espBlock : group.blocks) {
                double x1 = espBlock.x;
                double y1 = espBlock.y;
                double z1 = espBlock.z;
                double x2 = x1 + 1;
                double y2 = y1 + 1;
                double z2 = z1 + 1;

                minX = Math.min(minX, x1);
                minY = Math.min(minY, y1);
                minZ = Math.min(minZ, z1);
                maxX = Math.max(maxX, x2);
                maxY = Math.max(maxY, y2);
                maxZ = Math.max(maxZ, z2);

                // lines
                if (blockData.shapeMode.lines()) {
                    if (!espBlock.west && !espBlock.north) builder.yLine(lineColor, y1, y2, x1, z1);
                    if (!espBlock.west && !espBlock.south) builder.yLine(lineColor, y1, y2, x1, z2);
                    if (!espBlock.east && !espBlock.north) builder.yLine(lineColor, y1, y2, x2, z1);
                    if (!espBlock.east && !espBlock.south) builder.yLine(lineColor, y1, y2, x2, z2);

                    if (!espBlock.west && !espBlock.down)  builder.zLine(lineColor, z1, z2, x1, y1);
                    if (!espBlock.east && !espBlock.down)  builder.zLine(lineColor, z1, z2, x2, y1);
                    if (!espBlock.north && !espBlock.down) builder.xLine(lineColor, x1, x2, y1, z1);
                    if (!espBlock.south && !espBlock.down) builder.xLine(lineColor, x1, x2, y1, z2);

                    if (!espBlock.west && !espBlock.up)  builder.zLine(lineColor, z1, z2, x1, y2);
                    if (!espBlock.east && !espBlock.up)  builder.zLine(lineColor, z1, z2, x2, y2);
                    if (!espBlock.north && !espBlock.up) builder.xLine(lineColor, x1, x2, y2, z1);
                    if (!espBlock.south && !espBlock.up) builder.xLine(lineColor, x1, x2, y2, z2);
                }

                // faces
                if (blockData.shapeMode.sides()) {
                    if (!espBlock.up)    builder.quadHorizontal(sideColor, x1, y2, z1, x2, z2);
                    if (!espBlock.down)  builder.quadHorizontal(sideColor, x1, y1, z1, x2, z2);
                    if (!espBlock.north) builder.quadVertical(sideColor, x1, y1, z1, x2, y2, z1);
                    if (!espBlock.south) builder.quadVertical(sideColor, x1, y1, z2, x2, y2, z2);
                    if (!espBlock.east)  builder.quadVertical(sideColor, x2, y1, z1, x2, y2, z2);
                    if (!espBlock.west)  builder.quadVertical(sideColor, x1, y1, z1, x1, y2, z2);
                }
            }
        }

        builder.box(new Box(
            minX + pos.getStartX(),
            minY,
            minZ + pos.getStartZ(),
            maxX + pos.getStartX(),
            maxY,
            maxZ + pos.getStartZ()
        ));
    }
}
