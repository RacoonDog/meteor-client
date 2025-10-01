/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.mixin;

import com.mojang.blaze3d.opengl.GlStateManager;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;

@Mixin(GlStateManager.class)
public class GlStateManagerMixin {
    @Unique private static int viewportX;
    @Unique private static int viewportY;
    @Unique private static int viewportWidth;
    @Unique private static int viewportHeight;

    /**
     * @author Crosby
     * @reason Every {@link com.mojang.blaze3d.systems.RenderPass} calls this native function when it does not have to
     */
    @Overwrite
    public static void _viewport(int x, int y, int width, int height) {
        if (x != viewportX || y != viewportY || width != viewportWidth || height != viewportHeight) {
            viewportX = x;
            viewportY = y;
            viewportWidth = width;
            viewportHeight = height;
            GL11.glViewport(x, y, width, height);
        }
    }
}
