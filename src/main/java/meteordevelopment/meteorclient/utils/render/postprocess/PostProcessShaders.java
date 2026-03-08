package meteordevelopment.meteorclient.utils.render.postprocess;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.utils.PreInit;
import meteordevelopment.orbit.EventHandler;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class PostProcessShaders {
    public static EntityShader CHAMS;
    public static ESPGlowShader ESP_GLOW;
    public static PostProcessShader STORAGE_OUTLINE;

    private PostProcessShaders() {}

    @PreInit
    public static void init() {
        CHAMS = new ChamsShader();
        ESP_GLOW = new ESPGlowShader();
        STORAGE_OUTLINE = new StorageOutlineShader();

        MeteorClient.EVENT_BUS.subscribe(PostProcessShaders.class);
    }

    public static void beginRender() {
        CHAMS.clearTexture();
        ESP_GLOW.clearTexture();
        STORAGE_OUTLINE.clearTexture();
    }

    public static void submitEntityVertices() {
        CHAMS.submitVertices();
        ESP_GLOW.submitVertices();
    }

    @EventHandler
    private static void onRender(Render2DEvent event) {
        CHAMS.render();
        ESP_GLOW.render();
    }

    public static void onResized(int width, int height) {
        if (mc == null) return;

        CHAMS.onResized(width, height);
        ESP_GLOW.onResized(width, height);
        STORAGE_OUTLINE.onResized(width, height);
    }
}
