/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.render.fabe.uniforms;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.client.gl.DynamicUniformStorage;

import java.nio.ByteBuffer;

public class ColorUniform {
    public static final int SIZE = new Std140SizeCalculator()
        .putVec4()
        .get();

    private static final Data DATA = new Data();
    private static final DynamicUniformStorage<Data> STORAGE = new DynamicUniformStorage<>("Meteor - FABE Color UBO", SIZE, 16);

    public static void flipFrame() {
        STORAGE.clear();
    }

    public static GpuBufferSlice write(SettingColor color) {
        DATA.color = color;

        return STORAGE.write(DATA);
    }

    private static final class Data implements DynamicUniformStorage.Uploadable {
        private SettingColor color;

        @Override
        public void write(ByteBuffer buffer) {
            Std140Builder.intoBuffer(buffer)
                .putVec4(color.r / 255f, color.g / 255f, color.b / 255f, color.a / 255f);
        }

        @Override
        public boolean equals(Object o) {
            return false;
        }
    }
}
