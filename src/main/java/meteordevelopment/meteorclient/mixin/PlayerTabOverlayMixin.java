/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.BetterTab;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.BlitRenderState;
import net.minecraft.client.renderer.state.gui.ColoredRectangleRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.GuiTextRenderState;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import org.joml.Matrix3x2f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerTabOverlay.class)
public abstract class PlayerTabOverlayMixin {
    @ModifyConstant(constant = @Constant(longValue = 80L), method = "getPlayerInfos")
    private long modifyCount(long count) {
        BetterTab module = Modules.get().get(BetterTab.class);

        return module.isActive() ? module.tabSize.get() : count;
    }

    @Inject(method = "getNameForDisplay", at = @At("HEAD"), cancellable = true)
    public void getNameForDisplay(PlayerInfo info, CallbackInfoReturnable<Component> cir) {
        BetterTab betterTab = Modules.get().get(BetterTab.class);

        if (betterTab.isActive()) cir.setReturnValue(betterTab.getPlayerName(info));
    }

    @ModifyArg(method = "extractRenderState", at = @At(value = "INVOKE", target = "Ljava/lang/Math;min(II)I"), index = 0)
    private int modifyWidth(int width) {
        BetterTab module = Modules.get().get(BetterTab.class);

        return module.isActive() && module.accurateLatency.get() ? width + 30 : width;
    }

    @Inject(method = "extractRenderState", at = @At(value = "INVOKE", target = "Ljava/lang/Math;min(II)I", shift = At.Shift.BEFORE))
    private void modifyHeight(CallbackInfo ci, @Local(name = "rows") LocalIntRef rows, @Local(name = "cols") LocalIntRef cols, @Local(name = "slots") int playerCount) {
        BetterTab module = Modules.get().get(BetterTab.class);
        if (!module.isActive()) return;

        int newRows;
        int newCols = 1;
        int totalPlayers = newRows = playerCount;
        while (newRows > module.tabHeight.get()) {
            newRows = (totalPlayers + ++newCols - 1) / newCols;
        }

        rows.set(newRows);
        cols.set(newCols);
    }

    @Inject(method = "extractPingIcon", at = @At("HEAD"), cancellable = true)
    private void onExtractPingIcon(GuiGraphicsExtractor graphics, int slotWidth, int xo, int yo, PlayerInfo info, CallbackInfo ci) {
        BetterTab betterTab = Modules.get().get(BetterTab.class);

        if (betterTab.isActive() && betterTab.accurateLatency.get()) {
            Minecraft mc = Minecraft.getInstance();
            Font font = mc.font;

            int latency = Mth.clamp(info.getLatency(), 0, 9999);
            int color = latency < 150 ? 0xFF00E970 :
                latency < 300 ? 0xFFE7D020 : 0xFFD74238;
            String text = latency + "ms";

            if (betterTab.crosbyMode.get()) {
                GuiRenderState.Node current = ((GuiRenderStateAccessor) ((GuiGraphicsExtractorAccessor) graphics).getGuiRenderState()).meteor$getCurrent();
                current.addText(new GuiTextRenderState(font, Language.getInstance().getVisualOrder(FormattedText.of(text)), new Matrix3x2f(graphics.pose()), xo + slotWidth - font.width(text), yo, color, 0, true, false, ((GuiGraphicsExtractorAccessor) graphics).getScissorStack().peek()));
            } else {
                graphics.text(font, text, xo + slotWidth - font.width(text), yo, color);
            }

            ci.cancel();
        }
    }

    // I'm going crosby mode !!

    @Inject(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Options;getBackgroundColor(I)I"))
    private void saveRenderNodes(GuiGraphicsExtractor graphics, int screenWidth, Scoreboard scoreboard, Objective displayObjective, CallbackInfo ci, @Share("enabled") LocalBooleanRef enabledRef) {
        BetterTab betterTab = Modules.get().get(BetterTab.class);
        enabledRef.set(betterTab.isActive() && betterTab.crosbyMode.get());
    }

    @WrapOperation(method = "extractRenderState", slice = @Slice(
        from = @At(value = "INVOKE", target = "Lnet/minecraft/client/Options;getBackgroundColor(I)I"),
        to = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/PlayerTabOverlay;extractPingIcon(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIILnet/minecraft/client/multiplayer/PlayerInfo;)V")
    ), at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V"))
    private void wrapBackgroundExtraction(GuiGraphicsExtractor instance, int x0, int y0, int x1, int y1, int col, Operation<Void> original, @Share("enabled") LocalBooleanRef enabledRef) {
        if (enabledRef.get()) {
            GuiRenderState.Node current = ((GuiRenderStateAccessor) ((GuiGraphicsExtractorAccessor) instance).getGuiRenderState()).meteor$getCurrent();
            current.addGuiElement(new ColoredRectangleRenderState(RenderPipelines.GUI, TextureSetup.noTexture(), new Matrix3x2f(instance.pose()), x0, y0, x1, y1, col, col, ((GuiGraphicsExtractorAccessor) instance).getScissorStack().peek()));
        } else {
            original.call(instance, x0, y0, x1, y1, col);
        }
    }

    @WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/PlayerFaceExtractor;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/resources/Identifier;IIIZZI)V"))
    private void wrapPlayerHeadExtraction(GuiGraphicsExtractor graphics, Identifier texture, int x, int y, int size, boolean hat, boolean flip, int color, Operation<Void> original, @Share("enabled") LocalBooleanRef enabledRef) {
        if (enabledRef.get()) {
            GuiRenderState.Node current = ((GuiRenderStateAccessor) ((GuiGraphicsExtractorAccessor) graphics).getGuiRenderState()).meteor$getCurrent();
            float v = 8 + (flip ? 8 : 0);
            int height = 8 * (flip ? -1 : 1);
            blit(graphics, current, RenderPipelines.GUI_TEXTURED, texture, x, y, 8.0F, v, size, size, 8, height, 64, 64, color);
            if (hat) {
                blit(graphics, current, RenderPipelines.GUI_TEXTURED, texture, x, y, 40.0F, v, size, size, 8, height, 64, 64, color);
            }
        } else {
            original.call(graphics, texture, x, y, size, hat, flip, color);
        }
    }

    @WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V"))
    private void wrapPlayerNameExtraction(GuiGraphicsExtractor instance, Font font, Component str, int x, int y, int color, Operation<Void> original, @Share("enabled") LocalBooleanRef enabledRef) {
        if (enabledRef.get()) {
            GuiRenderState.Node current = ((GuiRenderStateAccessor) ((GuiGraphicsExtractorAccessor) instance).getGuiRenderState()).meteor$getCurrent();
            current.addText(new GuiTextRenderState(font, str.getVisualOrderText(), new Matrix3x2f(instance.pose()), x, y, color, 0, true, false, ((GuiGraphicsExtractorAccessor) instance).getScissorStack().peek()));
        } else {
            original.call(instance, font, str, x, y, color);
        }
    }

    @WrapOperation(method = "extractPingIcon", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V"))
    private void wrapPingExtraction(GuiGraphicsExtractor instance, RenderPipeline renderPipeline, Identifier location, int x, int y, int width, int height, Operation<Void> original) {
        BetterTab betterTab = Modules.get().get(BetterTab.class);
        if (betterTab.crosbyMode.get()) {
            GuiGraphicsExtractorAccessor accessor = (GuiGraphicsExtractorAccessor) instance;
            GuiRenderState.Node current = ((GuiRenderStateAccessor) accessor.getGuiRenderState()).meteor$getCurrent();
            TextureAtlasSprite sprite = accessor.meteor$getGuiSprites().getSprite(location);
            blit(instance, current, RenderPipelines.GUI_TEXTURED, sprite.atlasLocation(), x, y, width, height, sprite.getU0(), sprite.getU1(), sprite.getV0(), sprite.getV1(), -1);
        } else {
            original.call(instance, renderPipeline, location, x, y, width, height);
        }
    }

    @Unique
    private static void blit(GuiGraphicsExtractor graphics, GuiRenderState.Node node, RenderPipeline renderPipeline, Identifier location, int x, int y, float u, float v, int width, int height, int srcWidth, int srcHeight, int textureWidth, int textureHeight, int color) {
        blit(graphics, node, renderPipeline, location, x, y, width, height, (u + 0.0F) / (float) textureWidth, (u + (float) srcWidth) / (float) textureWidth, (v + 0.0F) / (float) textureHeight, (v + (float)srcHeight) / (float) textureHeight, color);
    }

    @Unique
    private static void blit(GuiGraphicsExtractor graphics, GuiRenderState.Node node, RenderPipeline renderPipeline, Identifier location, int x, int y, int width, int height, float u0, float u1, float v0, float v1, int color) {
        AbstractTexture texture = Minecraft.getInstance().getTextureManager().getTexture(location);
        node.addGuiElement(new BlitRenderState(renderPipeline, TextureSetup.singleTexture(texture.getTextureView(), texture.getSampler()), new Matrix3x2f(graphics.pose()), x, y, x + width, y + height, u0, u1, v0, v1, color, ((GuiGraphicsExtractorAccessor) graphics).getScissorStack().peek()));
    }
}
