/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import meteordevelopment.meteorclient.mixininterface.IEntityRenderState;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.esp.ESP;
import net.minecraft.client.render.entity.ArmorStandEntityRenderer;
import net.minecraft.client.render.entity.state.ArmorStandEntityRenderState;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ArmorStandEntityRenderer.class)
public class ArmorStandEntityRendererMixin {
    @Unique
    private static ESP esp;

    @ModifyExpressionValue(method = "getRenderLayer(Lnet/minecraft/client/render/entity/state/ArmorStandEntityRenderState;ZZZ)Lnet/minecraft/client/render/RenderLayer;", at = @At(value = "FIELD", target = "Lnet/minecraft/client/render/entity/state/ArmorStandEntityRenderState;marker:Z", opcode = Opcodes.GETFIELD))
    private boolean modifyMarkerValue(boolean original, @Local(argsOnly = true) ArmorStandEntityRenderState renderState) {
        if (esp == null) esp = Modules.get().get(ESP.class);

        return original && !(esp.isActive() && !esp.shouldSkip(((IEntityRenderState) renderState).meteor$getEntity()));
    }
}
