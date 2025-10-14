/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.render.fabe.uniforms;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import net.minecraft.client.gl.DynamicUniformStorage;

import java.nio.ByteBuffer;

public class PositionUniform {
    public static final int SIZE = new Std140SizeCalculator()
        .putVec3()
        .get();

    private static final Data DATA = new Data();
    private static final DynamicUniformStorage<Data> STORAGE = new DynamicUniformStorage<>("Meteor - FABE Position UBO", SIZE, 16);

    public static void flipFrame() {
        STORAGE.clear();
    }

    public static GpuBufferSlice write(float xPosition, float yPosition, float zPosition) {
        DATA.xPosition = xPosition;
        DATA.yPosition = yPosition;
        DATA.zPosition = zPosition;

        return STORAGE.write(DATA);
    }

    private static final class Data implements DynamicUniformStorage.Uploadable {
        private float xPosition;
        private float yPosition;
        private float zPosition;

        @Override
        public void write(ByteBuffer buffer) {
            Std140Builder.intoBuffer(buffer)
                .putVec3(xPosition, yPosition, zPosition);
        }

        @Override
        public boolean equals(Object o) {
            return false;
        }
    }
}
