/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.render.fabe;
import meteordevelopment.meteorclient.renderer.GpuMesh;
import meteordevelopment.meteorclient.systems.modules.render.blockesp.ESPBlockData;
import net.minecraft.block.Block;
import net.minecraft.util.math.Box;

import java.util.List;

public record FABEGpuGroupMesh(Box aabb, List<TracerLine> tracerLines, GpuMesh lines, GpuMesh faces) {
    public static FABEGpuGroupMesh upload(FABEMeshData mesh) {
        return new FABEGpuGroupMesh(
            mesh.aabb(),
            mesh.tracerLines(),
            GpuMesh.upload(mesh.lineBuilder()),
            GpuMesh.upload(mesh.faceBuilder())
        );
    }

    public void close() {
        this.lines.close();
        this.faces.close();
    }
}
