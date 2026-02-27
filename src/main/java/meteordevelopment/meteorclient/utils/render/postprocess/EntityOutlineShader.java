package meteordevelopment.meteorclient.utils.render.postprocess;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import meteordevelopment.meteorclient.renderer.FixedUniformStorage;
import meteordevelopment.meteorclient.renderer.MeshRenderer;
import meteordevelopment.meteorclient.renderer.MeteorRenderPipelines;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.ESP;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.DynamicUniformStorage;
import net.minecraft.client.util.Window;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;

import java.nio.ByteBuffer;

public class EntityOutlineShader extends EntityShader {
    private static ESP esp;
    private int previousWidth = -1;
    public int passes = -1;
    public float offset = -1f;
    private boolean initialized = false;

    private final GpuTextureView[] fbos = new GpuTextureView[4];
    private GpuBufferSlice[] ubos;

    public EntityOutlineShader() {
        super(MeteorRenderPipelines.POST_OUTLINE);
    }

    @Override
    protected boolean shouldDraw() {
        if (esp == null) esp = Modules.get().get(ESP.class);
        return esp.isShader();
    }

    @Override
    public boolean shouldDraw(Entity entity) {
        if (!shouldDraw()) return false;
        return !esp.shouldSkip(entity);
    }

    @Override
    protected void setupPass(MeshRenderer renderer) {}

    private GpuTextureView createFbo(int i) {
        int scale = (int) Math.pow(2, i);

        Window window = MinecraftClient.getInstance().getWindow();
        int screenWidth = window.getFramebufferWidth() / scale;
        int screenHeight = window.getFramebufferHeight() / scale;

        return RenderSystem.getDevice().createTextureView(RenderSystem.getDevice().createTexture("Entity Outline Blur - " + i, 15,  TextureFormat.RGBA8, screenWidth, screenHeight, 1, 1));
    }

    @Override
    public void onResized(int width, int height) {
        super.onResized(width, height);

        // clear old fbos
        for (GpuTextureView fbo : fbos) {
            if (fbo != null) fbo.close();
        }

        // create new fbos
        for (int i = 0; i < fbos.length; i++) {
            fbos[i] = createFbo(i);
        }
        initialized = true;

        // update ubos
        updateUniforms(offset);
    }

    @Override
    public void render() {
        if (this.isMaskEmpty || !this.shouldDraw()) return;

        if (!initialized) {
            for (int i = 0; i < fbos.length; i++) {
                fbos[i] = createFbo(i);
            }
            initialized = true;
        }

        int width = esp.outlineWidth.get();
        if (width != previousWidth) {
            previousWidth = width;

            passes = Math.min(MathHelper.ceil(width / 2.5d), 3);
            offset = (float) width / passes;

            // update ubos
            updateUniforms(offset);
        }

        // Initial downsample
        MeshRenderer.begin()
            .attachments(fbos[0], null)
            .pipeline(MeteorRenderPipelines.BLUR_ALPHA_DOWN)
            .fullscreen()
            .uniform("BlurData", ubos[0])
            .sampler("u_Texture", framebuffer.getColorAttachmentView(), RenderSystem.getSamplerCache().get(FilterMode.LINEAR))
            .end();

        // Downsample passes
        for (int i = 0; i < passes - 1; i++) {
            MeshRenderer.begin()
                .attachments(fbos[i + 1], null)
                .pipeline(MeteorRenderPipelines.BLUR_ALPHA_DOWN)
                .fullscreen()
                .uniform("BlurData", ubos[i + 1])
                .sampler("u_Texture", fbos[i], RenderSystem.getSamplerCache().get(FilterMode.LINEAR))
                .end();
        }

        // Upsample passes
        for (int i = passes - 1; i >= 1; i--) {
            MeshRenderer.begin()
                .attachments(fbos[i - 1], null)
                .pipeline(MeteorRenderPipelines.BLUR_ALPHA_UP)
                .fullscreen()
                .uniform("BlurData", ubos[i - 1])
                .sampler("u_Texture", fbos[i], RenderSystem.getSamplerCache().get(FilterMode.LINEAR))
                .end();
        }

        // Combination pass
        MeshRenderer.begin()
            .attachments(MinecraftClient.getInstance().getFramebuffer())
            .pipeline(MeteorRenderPipelines.POST_OUTLINE_NEW)
            .fullscreen()
            .sampler("u_MaskTexture", framebuffer.getColorAttachmentView(), RenderSystem.getSamplerCache().get(FilterMode.NEAREST))
            .sampler("u_BlurTexture", fbos[0], RenderSystem.getSamplerCache().get(FilterMode.LINEAR))
            .uniform("BlurData", ubos[0])
            .uniform("OutlineData", OutlineUniforms.write(
                esp.outlineWidth.get(),
                esp.fillOpacity.get().floatValue(),
                esp.shapeMode.get().ordinal(),
                esp.glowMultiplier.get().floatValue()))
            .end();
    }

    // Uniforms

    private void updateUniforms(float offset) {
        UNIFORM_STORAGE.clear();

        BlurUniformData[] uboData = new BlurUniformData[4];
        for (int i = 0; i < uboData.length; i++) {
            GpuTextureView fbo = fbos[i];
            uboData[i] = new BlurUniformData(
                0.5f / fbo.getWidth(0), 0.5f / fbo.getHeight(0),
                offset
            );
        }

        ubos = UNIFORM_STORAGE.writeAll(uboData);
    }

    private static final int UNIFORM_SIZE = new Std140SizeCalculator()
        .putVec2()
        .putFloat()
        .get();

    private static final FixedUniformStorage<BlurUniformData> UNIFORM_STORAGE = new FixedUniformStorage<>("Meteor - Entity Outline Blur UBO", UNIFORM_SIZE, 4);

    private record BlurUniformData(float halfTexelSizeX, float halfTexelSizeY, float offset) implements DynamicUniformStorage.Uploadable {
        @Override
        public void write(ByteBuffer buffer) {
            Std140Builder.intoBuffer(buffer)
                .putVec2(halfTexelSizeX, halfTexelSizeY)
                .putFloat(offset);
        }
    }
}
