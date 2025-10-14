/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.render.fabe;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import meteordevelopment.meteorclient.renderer.GpuMesh;
import meteordevelopment.meteorclient.systems.modules.render.blockesp.ESPBlockData;
import net.minecraft.block.Block;
import net.minecraft.util.math.Box;

import java.util.List;

public record FABEGpuGroupMesh(Block block, Box aabb, List<TracerLine> tracerLines, GpuMesh lines, GpuMesh faces, GpuBufferSlice lineColorUbo, GpuBufferSlice sideColorUbo) {
    public static FABEGpuGroupMesh upload(FABEMeshData mesh, FastAsyncBlockESP module) {
        ESPBlockData blockData = module.getBlockData(mesh.block());

        return new FABEGpuGroupMesh(
            mesh.block(),
            mesh.aabb(),
            mesh.tracerLines(),
            GpuMesh.upload(mesh.lineBuilder()),
            GpuMesh.upload(mesh.faceBuilder()),
            module.getOrCreateColorUbo(blockData.lineColor),
            module.getOrCreateColorUbo(blockData.sideColor)
        );
    }

    public FABEGpuGroupMesh updateColour(FastAsyncBlockESP module) {
        ESPBlockData blockData = module.getBlockData(this.block());

        return new FABEGpuGroupMesh(
            this.block(),
            this.aabb(),
            this.tracerLines(),
            this.lines(),
            this.faces(),
            module.getOrCreateColorUbo(blockData.lineColor),
            module.getOrCreateColorUbo(blockData.sideColor)
        );
    }

    public void close() {
        this.lines.close();
        this.faces.close();
    }
}
