/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.render.fabe;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
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

    public TracerLine getTracerLine(ChunkPos pos) {
        return new TracerLine(
            pos.getStartX() + (double) x / count + 0.5d,
            (double) y / count + 0.5d,
            pos.getStartZ() + (double) z / count + 0.5d
        );
    }

    public static FABEMeshData mesh(Block blockType, ChunkPos pos, List<FABEGroup> groups) {
        FABEMeshBuilder builder = new FABEMeshBuilder(blockType);

        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE;
        double maxX = Double.MIN_VALUE;
        double maxY = Double.MIN_VALUE;
        double maxZ = Double.MIN_VALUE;

        for (FABEGroup group : groups) {
            builder.tracerLine(group.getTracerLine(pos));

            for (FABEBlock block : group.blocks) {
                double x1 = block.x;
                double y1 = block.y;
                double z1 = block.z;
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
                if (!block.west && !block.north) builder.yLine(y1, y2, x1, z1);
                if (!block.west && !block.south) builder.yLine(y1, y2, x1, z2);
                if (!block.east && !block.north) builder.yLine(y1, y2, x2, z1);
                if (!block.east && !block.south) builder.yLine(y1, y2, x2, z2);

                if (!block.west && !block.down)  builder.zLine(z1, z2, x1, y1);
                if (!block.east && !block.down)  builder.zLine(z1, z2, x2, y1);
                if (!block.north && !block.down) builder.xLine(x1, x2, y1, z1);
                if (!block.south && !block.down) builder.xLine(x1, x2, y1, z2);

                if (!block.west && !block.up)  builder.zLine(z1, z2, x1, y2);
                if (!block.east && !block.up)  builder.zLine(z1, z2, x2, y2);
                if (!block.north && !block.up) builder.xLine(x1, x2, y2, z1);
                if (!block.south && !block.up) builder.xLine(x1, x2, y2, z2);

                // faces
                if (!block.up)    builder.quadHorizontal(x1, y2, z1, x2, z2);
                if (!block.down)  builder.quadHorizontal(x1, y1, z1, x2, z2);
                if (!block.north) builder.quadVertical(x1, y1, z1, x2, y2, z1);
                if (!block.south) builder.quadVertical(x1, y1, z2, x2, y2, z2);
                if (!block.east)  builder.quadVertical(x2, y1, z1, x2, y2, z2);
                if (!block.west)  builder.quadVertical(x1, y1, z1, x1, y2, z2);
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

        return builder.write();
    }
}
