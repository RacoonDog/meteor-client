/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.mixin;

import meteordevelopment.meteorclient.mixininterface.IFrustum;
import meteordevelopment.meteorclient.mixininterface.IFrustumIntersection;
import net.minecraft.client.render.Frustum;
import org.joml.FrustumIntersection;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(Frustum.class)
public class FrustumMixin implements IFrustum {
    @Shadow @Final private FrustumIntersection frustumIntersection;
    @Shadow private double x;
    @Shadow private double z;

    @Override
    public boolean meteor$intersectsColumn(float minX, float minZ, float maxX, float maxZ) {
        IFrustumIntersection intersection = (IFrustumIntersection) this.frustumIntersection;
        float f = (float)(minX - this.x);
        float g = (float)(minZ - this.z);
        float h = (float)(maxX - this.x);
        float i = (float)(maxZ - this.z);

        return intersection.meteor$intersectsColumn(f, g, h, i);
    }
}
