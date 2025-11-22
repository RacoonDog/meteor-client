/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.renderer;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import it.unimi.dsi.fastutil.ints.IntFloatImmutablePair;
import it.unimi.dsi.fastutil.objects.ReferenceArraySet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.game.ResolutionChangedEvent;
import meteordevelopment.meteorclient.mixininterface.IGpuTexture;
import meteordevelopment.meteorclient.utils.PostInit;
import meteordevelopment.orbit.listeners.ConsumerListener;
import net.minecraft.client.gl.DynamicUniformStorage;

import java.nio.ByteBuffer;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class BlurShader {
    private static final ReferenceSet<Object> BLUREES = new ReferenceArraySet<>();
    private static final GpuTextureView[] FBOS = new GpuTextureView[5];
    private static final GpuBufferSlice[] UBOS = new GpuBufferSlice[6];

    private static GpuTextureView FINAL_BO;

    // Strength-Levels from https://github.com/jonaburg/picom/blob/a8445684fe18946604848efb73ace9457b29bf80/src/backend/backend_common.c#L372
    // and CROSBYYY !! :tada: 😊
    private static final IntFloatImmutablePair[] STRENGTHS = new IntFloatImmutablePair[]{
        IntFloatImmutablePair.of(1, 0.50f),
        IntFloatImmutablePair.of(1, 1.25f), // LVL 1
        IntFloatImmutablePair.of(1, 2.25f), // LVL 2
        IntFloatImmutablePair.of(2, 1.0f),
        IntFloatImmutablePair.of(2, 1.5f),
        IntFloatImmutablePair.of(2, 2.0f),  // LVL 3
        IntFloatImmutablePair.of(2, 2.5f),
        IntFloatImmutablePair.of(2, 3.0f),  // LVL 4
        IntFloatImmutablePair.of(2, 3.5f),
        IntFloatImmutablePair.of(2, 4.25f), // LVL 5
        IntFloatImmutablePair.of(3, 2.5f),  // LVL 6
        IntFloatImmutablePair.of(3, 3.25f), // LVL 7
        IntFloatImmutablePair.of(3, 4.25f), // LVL 8
        IntFloatImmutablePair.of(3, 5.5f),  // LVL 9
        IntFloatImmutablePair.of(4, 3.25f), // LVL 10
        IntFloatImmutablePair.of(4, 4.0f),  // LVL 11
        IntFloatImmutablePair.of(4, 5.0f),  // LVL 12
        IntFloatImmutablePair.of(4, 6.0f),  // LVL 13
        IntFloatImmutablePair.of(4, 7.25f), // LVL 14
        IntFloatImmutablePair.of(4, 8.25f), // LVL 15
        IntFloatImmutablePair.of(5, 4.5f),  // LVL 16
        IntFloatImmutablePair.of(5, 5.25f), // LVL 17
        IntFloatImmutablePair.of(5, 6.25f), // LVL 18
        IntFloatImmutablePair.of(5, 7.25f), // LVL 19
        IntFloatImmutablePair.of(5, 8.5f)   // LVL 20
    };

    private static final int UNIFORM_SIZE = new Std140SizeCalculator()
        .putVec2()
        .putFloat()
        .get();

    private static final FixedUniformStorage<BlurUniformData> UNIFORM_STORAGE = new FixedUniformStorage<>("Meteor - Blur UBO", UNIFORM_SIZE, 6);

    private static float previousOffset = -1;
    private static boolean initialized = false;

    @PostInit
    public static void postInit() {
        MeteorClient.EVENT_BUS.subscribe(new ConsumerListener<>(ResolutionChangedEvent.class, event -> invalidateResources()));
    }

    public static void register(Object bluree) {
        BLUREES.add(bluree);
    }

    public static void unregister(Object bluree) {
        if (BLUREES.remove(bluree) && BLUREES.isEmpty()) {
            invalidateResources();
        }
    }

    public static int getStrengthCount() {
        return STRENGTHS.length;
    }

    public static void renderBlur(int strength) {
        renderBlur(strength, FullScreenRenderer.vbo, FullScreenRenderer.vbo);
    }

    public static void renderBlur(int strength, GpuBuffer vbo, GpuBuffer ibo) {
        // Update strength
        IntFloatImmutablePair strengthPair = STRENGTHS[strength];
        int iterations = strengthPair.leftInt();
        float offset = strengthPair.rightFloat();

        // Update framebuffers
        if (!initialized) {
            for (int i = 0; i < FBOS.length; i++) {
                FBOS[i] = createFbo(i);
            }

            FINAL_BO = RenderSystem.getDevice().createTextureView(RenderSystem.getDevice().createTexture("Blur - Output", 15,  TextureFormat.RGBA8, mc.getWindow().getFramebufferWidth(), mc.getWindow().getFramebufferHeight(), 1, 1));
            initialized = true;
        }

        // Update uniforms
        if (previousOffset != offset) {
            UNIFORM_STORAGE.clear();

            BlurUniformData[] uboData = new BlurUniformData[6];
            uboData[0] = new BlurUniformData(
                0.5f / mc.getFramebuffer().textureWidth, 0.5f / mc.getFramebuffer().textureHeight,
                offset
            );

            for (int i = 0; i < FBOS.length; i++) {
                GpuTextureView fbo = FBOS[i];
                uboData[i + 1] = new BlurUniformData(
                    0.5f / fbo.getWidth(0), 0.5f / fbo.getHeight(0),
                    offset
                );
            }

            GpuBufferSlice[] slices = UNIFORM_STORAGE.writeAll(uboData);

            System.arraycopy(slices, 0, UBOS, 0, slices.length);
            previousOffset = offset;
        }

        // Initial downsample
        renderToFbo(FBOS[0], mc.getFramebuffer().getColorAttachmentView(), MeteorRenderPipelines.BLUR_DOWN, UBOS[1], FullScreenRenderer.vbo, FullScreenRenderer.vbo);

        // Downsample
        for (int i = 0; i < iterations - 1; i++) {
            renderToFbo(FBOS[i + 1], FBOS[i], MeteorRenderPipelines.BLUR_DOWN, UBOS[i + 2], FullScreenRenderer.vbo, FullScreenRenderer.vbo);
        }

        // Upsample
        for (int i = iterations - 1; i >= 1; i--) {
            renderToFbo(FBOS[i - 1], FBOS[i], MeteorRenderPipelines.BLUR_UP, UBOS[i], FullScreenRenderer.vbo, FullScreenRenderer.vbo);
        }

        // Final upsample
        renderToFbo(FINAL_BO, FBOS[0], MeteorRenderPipelines.BLUR_UP, UBOS[0], vbo, ibo);

        // deblugging
        TextureUtil.writeAsPNG(MeteorClient.FOLDER.toPath(), "output_fbo", FINAL_BO.texture(), 0, c -> c);

        RenderSystem.getDevice().createCommandEncoder().presentTexture(FINAL_BO);
    }

    private static void renderToFbo(GpuTextureView targetFbo, GpuTextureView sourceTexture, RenderPipeline pipeline, GpuBufferSlice ubo, GpuBuffer vbo, GpuBuffer ibo) {
        AddressMode prevAddressModeU = ((IGpuTexture) sourceTexture.texture()).meteor$getAddressModeU();
        AddressMode prevAddressModeV = ((IGpuTexture) sourceTexture.texture()).meteor$getAddressModeV();

        sourceTexture.texture().setAddressMode(AddressMode.CLAMP_TO_EDGE);

        MeshRenderer.begin()
            .attachments(targetFbo, null)
            .pipeline(pipeline)
            .mesh(vbo, ibo)
            .uniform("BlurData", ubo)
            .sampler("u_Texture", sourceTexture)
            .end();

        sourceTexture.texture().setAddressMode(prevAddressModeU, prevAddressModeV);
    }

    private static GpuTextureView createFbo(int i) {
        double scale = 1 / Math.pow(2, i + 1);

        int width = (int) (mc.getWindow().getFramebufferWidth() * scale);
        int height = (int) (mc.getWindow().getFramebufferHeight() * scale);

        return RenderSystem.getDevice().createTextureView(RenderSystem.getDevice().createTexture("Blur - " + i, 15,  TextureFormat.RGBA8, width, height, 1, 1));
    }

    private static void invalidateResources() {
        // Invalidate fbos
        for (int i = 0; i < FBOS.length; i++) {
            GpuTextureView fbo = FBOS[i];
            if (fbo != null) {
                fbo.close();
                FBOS[i] = null;
            }
        }

        if (FINAL_BO != null) {
            FINAL_BO.close();
            FINAL_BO = null;
        }

        initialized = false;

        // Invalidate ubos
        if (previousOffset != -1) {
            UNIFORM_STORAGE.clear();
        }
        previousOffset = -1;
    }

    private record BlurUniformData(float halfTexelSizeX, float halfTexelSizeY, float offset) implements DynamicUniformStorage.Uploadable {
        @Override
        public void write(ByteBuffer buffer) {
            Std140Builder.intoBuffer(buffer)
                .putVec2(halfTexelSizeX, halfTexelSizeY)
                .putFloat(offset);
        }
    }
}
