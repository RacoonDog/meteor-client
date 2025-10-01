/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.render.fabe;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import net.minecraft.client.gl.DynamicUniformStorage;
import org.joml.Vector3fc;
import org.joml.Vector4fc;

import java.nio.ByteBuffer;

public class FABEMeshUniforms {
    public static final int SIZE = new Std140SizeCalculator()
        .putVec4()
        .putVec4()
        .get();

    private static final Data DATA = new Data();

    private static final DynamicUniformStorage<Data> STORAGE = new DynamicUniformStorage<>("Meteor - FABE Mesh UBO", SIZE, 16);

    public static void flipFrame() {
        STORAGE.clear();
    }

    public static GpuBufferSlice write(Vector3fc offset, Vector4fc color) {
        DATA.offset = offset;
        DATA.color = color;

        return STORAGE.write(DATA);
    }

    private static final class Data implements DynamicUniformStorage.Uploadable {
        private Vector3fc offset;
        private Vector4fc color;

        @Override
        public void write(ByteBuffer buffer) {
            Std140Builder.intoBuffer(buffer)
                .putVec3(offset)
                .putVec4(color);
        }

        @Override
        public boolean equals(Object o) {
            return false;
        }
    }
}
