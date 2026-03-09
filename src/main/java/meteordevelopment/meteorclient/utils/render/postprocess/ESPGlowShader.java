package meteordevelopment.meteorclient.utils.render.postprocess;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.game.ResourcePacksReloadedEvent;
import meteordevelopment.meteorclient.mixininterface.IWorldRenderer;
import meteordevelopment.meteorclient.renderer.MeshRenderer;
import meteordevelopment.meteorclient.renderer.MeteorRenderPipelines;
import meteordevelopment.meteorclient.renderer.Texture;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.esp.ESP;
import meteordevelopment.meteorclient.systems.modules.render.esp.ESPEntityData;
import meteordevelopment.meteorclient.utils.OutlineRenderCommandQueue;
import meteordevelopment.meteorclient.utils.PostInit;
import meteordevelopment.meteorclient.utils.render.CustomOutlineVertexConsumerProvider;
import meteordevelopment.meteorclient.utils.render.NoopImmediateVertexConsumerProvider;
import meteordevelopment.meteorclient.utils.render.NoopOutlineVertexConsumerProvider;
import meteordevelopment.meteorclient.utils.render.WrapperImmediateVertexConsumerProvider;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.DynamicUniformStorage;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.command.RenderDispatcher;
import net.minecraft.client.util.Window;
import net.minecraft.entity.Entity;
import net.minecraft.resource.Resource;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class ESPGlowShader extends EntityShader {
    private static final String[] FILE_FORMATS = { "png", "jpg" };

    private static Texture IMAGE_TEX;
    private static ESP esp;

    private final Map<ESPRenderKey, ESPRenderBatch> espRenderBatchMap = new Object2ObjectOpenHashMap<>();
    private final GpuTextureView[] fbos = new GpuTextureView[4];
    private boolean initialized = false;

    public ESPGlowShader() {
        super(MeteorRenderPipelines.POST_OUTLINE_GLOW);
        MeteorClient.EVENT_BUS.subscribe(ESPGlowShader.class);
    }

    @PostInit
    public static void load() {
        try {
            ByteBuffer data = null;
            for (String fileFormat : FILE_FORMATS) {
                Optional<Resource> optional = mc.getResourceManager().getResource(MeteorClient.identifier("textures/chams." + fileFormat));
                if (optional.isEmpty() || optional.get().getInputStream() == null) {
                    continue;
                }

                data = TextureUtil.readResource(optional.get().getInputStream());
                break;
            }
            if (data == null) return;

            data.rewind();

            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer width = stack.mallocInt(1);
                IntBuffer height = stack.mallocInt(1);
                IntBuffer comp = stack.mallocInt(1);

                STBImage.stbi_set_flip_vertically_on_load(true);
                ByteBuffer image = STBImage.stbi_load_from_memory(data, width, height, comp, 4);

                IMAGE_TEX = new Texture(width.get(0), height.get(0), TextureFormat.RGBA8, FilterMode.NEAREST, FilterMode.NEAREST);
                IMAGE_TEX.upload(image);

                STBImage.stbi_image_free(image);
                STBImage.stbi_set_flip_vertically_on_load(false);
            }
        }
        catch (IOException e) {
            MeteorClient.LOG.error("Error loading the esp glow texture shader", e);
        }
    }

    @EventHandler
    private static void onResourcePacksReloaded(ResourcePacksReloadedEvent event) {
        load();
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

    private static GpuTextureView createFbo(int i) {
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

        // update batch framebuffers
        for (ESPRenderBatch batch : this.espRenderBatchMap.values()) {
            batch.framebuffer.resize(width, height);
            batch.targetFbo.close();
            batch.targetFbo = createFbo(0);
        }
    }

    @Override
    public void render() {
        if (!this.shouldDraw()) return;

        if (!initialized) {
            for (int i = 0; i < fbos.length; i++) {
                fbos[i] = createFbo(i);
            }
            initialized = true;
        }

        this.espRenderBatchMap.values().removeIf(batch -> !batch.used);
        if (this.espRenderBatchMap.isEmpty()) return;

        this.espRenderBatchMap.forEach((options, batch) -> {
            if (batch.isMaskEmpty) return;

            int width = options.outlineWidth;
            int passes = Math.min(MathHelper.ceil(width / 2.5d), 3);
            float offset = (float) width / passes;

            GpuBufferSlice[] ubos = uploadUniforms(offset);

            // Initial downsample
            try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> MeteorClient.NAME + " ESPGlowShader downsample pass #1",
                fbos[0], OptionalInt.empty())) {
                pass.setPipeline(MeteorRenderPipelines.BLUR_ALPHA_DOWN);
                pass.setUniform("BlurData", ubos[0]);
                pass.bindTexture("u_Texture", batch.framebuffer.getColorAttachmentView(), RenderSystem.getSamplerCache().get(FilterMode.LINEAR));
                pass.draw(0, 3);
            }

            // Downsample passes
            for (int i = 0; i < passes - 1; i++) {
                int fi = i;
                try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> MeteorClient.NAME + " ESPGlowShader downsample pass #" + (fi + 2),
                    fbos[i + 1], OptionalInt.empty())) {
                    pass.setPipeline(MeteorRenderPipelines.BLUR_ALPHA_DOWN);
                    pass.setUniform("BlurData", ubos[i + 1]);
                    pass.bindTexture("u_Texture", fbos[i], RenderSystem.getSamplerCache().get(FilterMode.LINEAR));
                    pass.draw(0, 3);
                }
            }

            // Upsample passes
            for (int i = passes - 1; i >= 2; i--) {
                int fi = passes - i;
                try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> MeteorClient.NAME + " ESPGlowShader upsample pass #" + (fi + 2),
                    fbos[i - 1], OptionalInt.empty())) {
                    pass.setPipeline(MeteorRenderPipelines.BLUR_UP);
                    pass.setUniform("BlurData", ubos[i - 1]);
                    pass.bindTexture("u_Texture", fbos[i], RenderSystem.getSamplerCache().get(FilterMode.LINEAR));
                    pass.draw(0, 3);
                }
            }

            // Last upsample pass
            try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> MeteorClient.NAME + " ESPGlowShader upsample pass #last",
                batch.targetFbo, OptionalInt.empty())) {
                pass.setPipeline(MeteorRenderPipelines.BLUR_UP);
                pass.setUniform("BlurData", ubos[0]);
                pass.bindTexture("u_Texture", fbos[0], RenderSystem.getSamplerCache().get(FilterMode.LINEAR));
                pass.draw(0, 3);
            }

            batch.blurUbo = ubos[0];
            batch.outlineUbo = OutlineUniforms.write(
                options.outlineWidth,
                options.fillOpacity,
                options.shapeMode,
                options.glowMultiplier,
                options.colorBlendMode);

            batch.used = false;
        });

        // Combination pass
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> MeteorClient.NAME + " ESPGlowShader combination pass",
            MinecraftClient.getInstance().getFramebuffer().getColorAttachmentView(), OptionalInt.empty())) {

            this.espRenderBatchMap.forEach((options, batch) -> {
                ESP.ShaderMode shaderMode = options.shaderMode;
                pass.setPipeline(shaderMode == ESP.ShaderMode.Glow ? MeteorRenderPipelines.POST_OUTLINE_GLOW : MeteorRenderPipelines.POST_OUTLINE_GLOW_TEX);

                // bind textures
                pass.bindTexture("u_MaskTexture", batch.framebuffer.getColorAttachmentView(), RenderSystem.getSamplerCache().get(FilterMode.NEAREST));
                pass.bindTexture("u_BlurTexture", batch.targetFbo, RenderSystem.getSamplerCache().get(FilterMode.LINEAR));
                if (shaderMode == ESP.ShaderMode.Glow_Texture) {
                    pass.bindTexture("u_OverlayTexture", IMAGE_TEX.getGlTextureView(), RenderSystem.getSamplerCache().get(FilterMode.LINEAR));
                }

                // bind uniforms
                pass.setUniform("BlurData", batch.blurUbo);
                pass.setUniform("OutlineData", batch.outlineUbo);

                pass.draw(0, 3);
            });
        }

        // clear data
        UNIFORM_STORAGE.clear();
    }

    // Batching

    private record ESPRenderKey(int outlineWidth, float fillOpacity, int shapeMode, float glowMultiplier, int colorBlendMode, ESP.ShaderMode shaderMode) {}

    public static class ESPRenderBatch {
        public final CustomOutlineVertexConsumerProvider vertexConsumerProvider = new CustomOutlineVertexConsumerProvider();
        public final OutlineRenderCommandQueue commandQueue = new OutlineRenderCommandQueue();
        public final RenderDispatcher dispatcher;
        public final Framebuffer framebuffer = new SimpleFramebuffer(MeteorClient.NAME + " ESP Glow Shader", mc.getWindow().getFramebufferWidth(), mc.getWindow().getFramebufferHeight(), true);
        private GpuTextureView targetFbo;
        private GpuBufferSlice blurUbo;
        private GpuBufferSlice outlineUbo;
        private boolean used;
        private boolean isMaskEmpty;

        private ESPRenderBatch(ESPRenderKey options) {
            this.dispatcher = new RenderDispatcher(
                this.commandQueue,
                mc.getBlockRenderManager(),
                new WrapperImmediateVertexConsumerProvider(() -> vertexConsumerProvider),
                mc.getAtlasManager(),
                NoopOutlineVertexConsumerProvider.INSTANCE,
                NoopImmediateVertexConsumerProvider.INSTANCE,
                mc.textRenderer
            );

            this.targetFbo = createFbo(0);
        }

        public void close() {
            dispatcher.close();
            framebuffer.delete();
            targetFbo.close();
        }
    }

    public ESPRenderBatch getBatch(ESPEntityData entityData) {
        ESPRenderBatch batch = this.espRenderBatchMap.computeIfAbsent(new ESPRenderKey(
            entityData.outlineWidth.get(),
            entityData.fillOpacity.get().floatValue(),
            entityData.shapeMode.get().ordinal(),
            entityData.glowMultiplier.get().floatValue(),
            entityData.colorBlendMode.get().ordinal(),
            entityData.shaderMode.get()
        ), ESPRenderBatch::new);
        batch.used = true;
        return batch;
    }

    public Collection<ESPRenderBatch> getBatches() {
        return this.espRenderBatchMap.values();
    }

    @Override
    public void submitVertices() {
        if (!shouldDraw()) return;

        for (ESPRenderBatch batch : this.getBatches()) {
            ((IWorldRenderer) mc.worldRenderer).meteor$pushEntityOutlineFramebuffer(batch.framebuffer);

            batch.isMaskEmpty = batch.vertexConsumerProvider.isEmpty();
            batch.vertexConsumerProvider.draw();

            ((IWorldRenderer) mc.worldRenderer).meteor$popEntityOutlineFramebuffer();
        }
    }

    @Override
    public void clearTexture() {
        if (!shouldDraw()) return;

        for (ESPRenderBatch batch : this.getBatches()) {
            RenderSystem.getDevice().createCommandEncoder().clearColorTexture(batch.framebuffer.getColorAttachment(), 0);
        }
    }

    // Uniforms

    private GpuBufferSlice[] uploadUniforms(float offset) {
        BlurUniformData[] uboData = new BlurUniformData[4];
        for (int i = 0; i < uboData.length; i++) {
            GpuTextureView fbo = fbos[i];
            uboData[i] = new BlurUniformData(
                0.5f / fbo.getWidth(0), 0.5f / fbo.getHeight(0),
                offset
            );
        }

        return UNIFORM_STORAGE.writeAll(uboData);
    }

    private static final int UNIFORM_SIZE = new Std140SizeCalculator()
        .putVec2()
        .putFloat()
        .get();

    private static final DynamicUniformStorage<BlurUniformData> UNIFORM_STORAGE = new DynamicUniformStorage<>("Meteor - Entity Outline Blur UBO", UNIFORM_SIZE, 4);

    private record BlurUniformData(float halfTexelSizeX, float halfTexelSizeY, float offset) implements DynamicUniformStorage.Uploadable {
        @Override
        public void write(ByteBuffer buffer) {
            Std140Builder.intoBuffer(buffer)
                .putVec2(halfTexelSizeX, halfTexelSizeY)
                .putFloat(offset);
        }
    }
}
