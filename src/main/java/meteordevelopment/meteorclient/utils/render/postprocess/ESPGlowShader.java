package meteordevelopment.meteorclient.utils.render.postprocess;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.game.ResourcePacksReloadedEvent;
import meteordevelopment.meteorclient.renderer.FixedUniformStorage;
import meteordevelopment.meteorclient.renderer.MeshRenderer;
import meteordevelopment.meteorclient.renderer.MeteorRenderPipelines;
import meteordevelopment.meteorclient.renderer.Texture;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.ESP;
import meteordevelopment.meteorclient.utils.PostInit;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.DynamicUniformStorage;
import net.minecraft.client.util.Window;
import net.minecraft.entity.Entity;
import net.minecraft.resource.Resource;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Optional;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class ESPGlowShader extends EntityShader {
    private static final String[] FILE_FORMATS = { "png", "jpg" };

    private static Texture IMAGE_TEX;
    private static ESP esp;
    private int previousWidth = -1;
    public int passes = -1;
    public float offset = -1f;
    private boolean initialized = false;

    private final GpuTextureView[] fbos = new GpuTextureView[4];
    private GpuBufferSlice[] ubos;

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
                .pipeline(MeteorRenderPipelines.BLUR_UP)
                .fullscreen()
                .uniform("BlurData", ubos[i - 1])
                .sampler("u_Texture", fbos[i], RenderSystem.getSamplerCache().get(FilterMode.LINEAR))
                .end();
        }

        ESP.ShaderMode shaderMode = esp.shaderMode.get();

        // Combination pass
        MeshRenderer renderer = MeshRenderer.begin()
            .attachments(MinecraftClient.getInstance().getFramebuffer())
            .pipeline(shaderMode == ESP.ShaderMode.Glow ? MeteorRenderPipelines.POST_OUTLINE_GLOW : MeteorRenderPipelines.POST_OUTLINE_GLOW_TEX)
            .fullscreen()
            .sampler("u_MaskTexture", framebuffer.getColorAttachmentView(), RenderSystem.getSamplerCache().get(FilterMode.NEAREST))
            .sampler("u_BlurTexture", fbos[0], RenderSystem.getSamplerCache().get(FilterMode.LINEAR))
            .uniform("BlurData", ubos[0])
            .uniform("OutlineData", OutlineUniforms.write(
                esp.outlineWidth.get(),
                esp.fillOpacity.get().floatValue(),
                esp.shapeMode.get().ordinal(),
                esp.glowMultiplier.get().floatValue(),
                esp.colorBlendMode.get().ordinal()));

        if (shaderMode == ESP.ShaderMode.Glow_Texture) {
            renderer.sampler("u_OverlayTexture", IMAGE_TEX.getGlTextureView(), RenderSystem.getSamplerCache().get(FilterMode.LINEAR));
        }

        renderer.end();
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
