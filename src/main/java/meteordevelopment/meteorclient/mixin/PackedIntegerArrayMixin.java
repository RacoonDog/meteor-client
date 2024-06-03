/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.mixin;

import meteordevelopment.meteorclient.mixininterface.IPackedIntegerArray;
import net.minecraft.util.collection.PackedIntegerArray;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(PackedIntegerArray.class)
public abstract class PackedIntegerArrayMixin implements IPackedIntegerArray {
    @Shadow @Final private long[] data;
    @Shadow @Final private int elementsPerLong;
    @Shadow @Final private long maxValue;
    @Shadow @Final private int elementBits;
    @Shadow @Final private int size;

    @Shadow
    public abstract void method_39892(int[] is);

    @Shadow
    protected abstract int getStorageIndex(int index);

    @Override
    public void meteor$unpackPartially(int[] array, int startIndex, int endIndex) {
        startIndex = getStorageIndex(startIndex);
        endIndex = getStorageIndex(endIndex);

        boolean matchesEnd = endIndex == this.data.length - 1;
        if (matchesEnd) {
            if (startIndex == 0) {
                // if both beginning and end match, shrimply use the regular method
                this.method_39892(array);
                return;
            }
            endIndex -= 1;
        }

        int arrayIndex = startIndex * this.elementsPerLong;

        int index;
        for (index = startIndex; index <= endIndex; ++index) {
            long l = this.data[index];

            for (int bleh = 0; bleh < this.elementsPerLong; ++bleh) {
                array[arrayIndex + bleh] = (int) (l & this.maxValue);
                l >>= this.elementBits;
            }

            arrayIndex += this.elementsPerLong;
        }

        if (matchesEnd) {
            index = this.size - arrayIndex;
            long l = this.data[endIndex + 1];

            for (int bleh = 0; bleh < index; ++bleh) {
                array[arrayIndex + bleh] = (int) (l & this.maxValue);
                l >>= this.elementBits;
            }
        }
    }
}
