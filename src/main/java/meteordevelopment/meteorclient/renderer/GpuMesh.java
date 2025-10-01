/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.renderer;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;

public record GpuMesh(
    GpuBuffer vertices,
    GpuBuffer indices,
    int indexCount
) {
    public static GpuMesh upload(MeshBuilder meshBuilder) {
        return new GpuMesh(
            RenderSystem.getDevice().createBuffer(() -> "GpuMesh Vertices", GpuBuffer.USAGE_VERTEX, meshBuilder.getVertices()),
            RenderSystem.getDevice().createBuffer(() -> "GpuMesh Indices", GpuBuffer.USAGE_INDEX, meshBuilder.getIndices()),
            meshBuilder.getIndicesCount()
        );
    }

    public void bind(RenderPass pass) {
        pass.setVertexBuffer(0, this.vertices());
        pass.setIndexBuffer(this.indices(), VertexFormat.IndexType.INT);
    }

    public void close() {
        this.vertices().close();
        this.indices().close();
    }
}
