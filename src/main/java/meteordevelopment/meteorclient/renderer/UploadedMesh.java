/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.renderer;

import com.mojang.blaze3d.buffers.GpuBuffer;
import org.jetbrains.annotations.Nullable;

public record UploadedMesh(@Nullable GpuBuffer vbo, @Nullable GpuBuffer ibo) {
    public static final UploadedMesh EMPTY = new UploadedMesh(null, null);
}
