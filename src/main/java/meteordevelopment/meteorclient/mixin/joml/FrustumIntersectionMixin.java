/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.mixin.joml;

import meteordevelopment.meteorclient.mixininterface.IFrustumIntersection;
import org.joml.FrustumIntersection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = FrustumIntersection.class, remap = false)
public class FrustumIntersectionMixin implements IFrustumIntersection {
    @Shadow private float nxX;
    @Shadow private float nxY;
    @Shadow private float nxZ;
    @Shadow private float nxW;
    @Shadow private float pxX;
    @Shadow private float pxY;
    @Shadow private float pxZ;
    @Shadow private float pxW;
    @Shadow private float nyX;
    @Shadow private float nyY;
    @Shadow private float nyZ;
    @Shadow private float nyW;
    @Shadow private float pyX;
    @Shadow private float pyY;
    @Shadow private float pyZ;
    @Shadow private float pyW;
    @Shadow private float nzX;
    @Shadow private float nzY;
    @Shadow private float nzZ;
    @Shadow private float nzW;
    @Shadow private float pzW;
    @Shadow private float pzX;
    @Shadow private float pzZ;
    @Shadow private float pzY;

    @Override
    public boolean meteor$intersectsColumn(float minX, float minZ, float maxX, float maxZ) {
        if (this.nxX * (this.nxX < 0.0F ? minX : maxX) + this.nxY * (this.nxY < 0.0F ? Float.NEGATIVE_INFINITY : Float.POSITIVE_INFINITY) + this.nxZ * (this.nxZ < 0.0F ? minZ : maxZ) >= -this.nxW) {
            if (this.pxX * (this.pxX < 0.0F ? minX : maxX) + this.pxY * (this.pxY < 0.0F ? Float.NEGATIVE_INFINITY : Float.POSITIVE_INFINITY) + this.pxZ * (this.pxZ < 0.0F ? minZ : maxZ) >= -this.pxW) {
                if (this.nyX * (this.nyX < 0.0F ? minX : maxX) + this.nyY * (this.nyY < 0.0F ? Float.NEGATIVE_INFINITY : Float.POSITIVE_INFINITY) + this.nyZ * (this.nyZ < 0.0F ? minZ : maxZ) >= -this.nyW) {
                    if (this.pyX * (this.pyX < 0.0F ? minX : maxX) + this.pyY * (this.pyY < 0.0F ? Float.NEGATIVE_INFINITY : Float.POSITIVE_INFINITY) + this.pyZ * (this.pyZ < 0.0F ? minZ : maxZ) >= -this.pyW) {
                        if (this.nzX * (this.nzX < 0.0F ? minX : maxX) + this.nzY * (this.nzY < 0.0F ? Float.NEGATIVE_INFINITY : Float.POSITIVE_INFINITY) + this.nzZ * (this.nzZ < 0.0F ? minZ : maxZ) >= -this.nzW) {
                            if (this.pzX * (this.pzX < 0.0F ? minX : maxX) + this.pzY * (this.pzY < 0.0F ? Float.NEGATIVE_INFINITY : Float.POSITIVE_INFINITY) + this.pzZ * (this.pzZ < 0.0F ? minZ : maxZ) >= -this.pzW) {
                                return true;
                            }
                        }
                    }
                }
            }
        }

        return false;
    }
}
