/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.render.fabe;

import meteordevelopment.meteorclient.renderer.GpuMesh;
import net.minecraft.util.math.Box;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public record FABEGpuGroupMesh(Box aabb, List<TracerLine> tracerLines, @Nullable GpuMesh lines, @Nullable GpuMesh faces) {
    public void close() {
        if (this.lines() != null) {
            this.lines().close();
        }

        if (this.faces() != null) {
            this.faces().close();
        }
    }
}
