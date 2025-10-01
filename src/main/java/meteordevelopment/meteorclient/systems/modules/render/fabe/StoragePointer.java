/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.render.fabe;

import net.minecraft.world.chunk.Chunk;

public class StoragePointer {
    private final int bottomY;
    private int index = 0;
    private int yIndex = 0;

    public StoragePointer(Chunk chunk) {
        this.bottomY = chunk.getBottomY();
    }

    public void increment() {
        this.index++;
    }

    public void nextSection() {
        this.yIndex++;
        this.index = 0;
    }

    public short x() {
        return (short) (this.index & 15);
    }

    public short y() {
        return (short) (((this.index >> 8) & 15) + this.bottomY + this.yIndex * 16);
    }

    public short z() {
        return (short) ((this.index >> 4) & 15);
    }

    public static long key(int x, int y, int z ) {
        return ((long) y << 16) | ((long) (z & 15) << 8) | ((long) (x & 15));
    }
}
