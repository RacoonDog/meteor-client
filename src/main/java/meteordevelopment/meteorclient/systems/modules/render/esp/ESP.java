/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.render.esp;

import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.Renderer2D;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.config.Config;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.render.NametagUtils;
import meteordevelopment.meteorclient.utils.render.WireframeEntityRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

public class ESP extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgColors = settings.createGroup("Colors");

    // General

    public final Setting<ESPEntityData> defaults = sgGeneral.add(new GenericSetting.Builder<ESPEntityData>()
        .name("defaults")
        .description("The default ESP entity selection data.")
        .defaultValue(new ESPEntityData())
        .build()
    );

    public final EntitySelectionDataSetting<ESPEntityData> entityDatas = sgGeneral.add(new EntitySelectionDataSetting.Builder<ESPEntityData>()
        .name("entity-configs")
        .description("The configs.")
        .defaultData(defaults)
        .build()
    );

    public final Setting<Boolean> highlightTarget = sgGeneral.add(new BoolSetting.Builder()
        .name("highlight-target")
        .description("highlights the currently targeted entity differently")
        .defaultValue(false)
        .build()
    );

    public final Setting<Boolean> targetHitbox = sgGeneral.add(new BoolSetting.Builder()
        .name("target-hitbox")
        .description("draw the hitbox of the target entity")
        .defaultValue(true)
        .visible(highlightTarget::get)
        .build()
    );

    public final Setting<Boolean> ignoreSelf = sgGeneral.add(new BoolSetting.Builder()
        .name("ignore-self")
        .description("Ignores yourself drawing the shader.")
        .defaultValue(true)
        .build()
    );

    // Colors

    public final Setting<Boolean> friendOverride = sgColors.add(new BoolSetting.Builder()
        .name("show-friend-colors")
        .description("Whether or not to override the distance/health color of friends with the friend color.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> targetColor = sgColors.add(new ColorSetting.Builder()
        .name("target-color")
        .description("The target color.")
        .defaultValue(new SettingColor(200, 200, 200, 255))
        .visible(highlightTarget::get)
        .build()
    );

    private final Setting<SettingColor> targetHitboxColor = sgColors.add(new ColorSetting.Builder()
        .name("target-hitbox-color")
        .description("The target hitbox color.")
        .defaultValue(new SettingColor(100, 200, 200, 255))
        .visible(() -> highlightTarget.get() && targetHitbox.get())
        .build()
    );

    private final Color lineColor = new Color();
    private final Color sideColor = new Color();
    private final Color baseColor = new Color();

    private final Vector3d pos1 = new Vector3d();
    private final Vector3d pos2 = new Vector3d();
    private final Vector3d pos = new Vector3d();

    private int count;

    public ESP() {
        super(Categories.Render, "esp", "Renders entities through walls.");
    }

    // Box

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        count = 0;

        Entity target = null;
        if (highlightTarget.get() && targetHitbox.get() && mc.crosshairTarget instanceof EntityHitResult hr) target = hr.getEntity();

        for (Entity entity : mc.world.getEntities()) {
            @Nullable ESPEntityData entityData = getEntityData(entity);
            if (target != entity && (entityData == null || shouldSkip(entity))) continue;
            if (target == entity || entityData.mode.get() == Mode.Box || entityData.mode.get() == Mode.Wireframe) drawBoundingBox(event, entityData, entity);
            count++;
        }
    }

    private void drawBoundingBox(Render3DEvent event, ESPEntityData entityData, Entity entity) {
        Color color = getColor(entityData, entity);
        if (color != null) {
            lineColor.set(color);
            sideColor.set(color).a((int) (sideColor.a * entityData.fillOpacity.get()));
        }

        if (entityData.mode.get() == Mode.Wireframe) {
            WireframeEntityRenderer.render(event, entity, 1, sideColor, lineColor, entityData.shapeMode.get());
        }

        boolean target = drawAsTarget(entity);

        if (entityData.mode.get() == Mode.Box || (targetHitbox.get() && target)) {
            double x = MathHelper.lerp(event.tickDelta, entity.lastRenderX, entity.getX()) - entity.getX();
            double y = MathHelper.lerp(event.tickDelta, entity.lastRenderY, entity.getY()) - entity.getY();
            double z = MathHelper.lerp(event.tickDelta, entity.lastRenderZ, entity.getZ()) - entity.getZ();

            ShapeMode shape = entityData.shapeMode.get();
            if (target && entityData.mode.get() != Mode.Box) shape = ShapeMode.Lines;
            if (target) lineColor.set(targetHitboxColor.get());

            Box box = entity.getBoundingBox();
            event.renderer.box(x + box.minX, y + box.minY, z + box.minZ, x + box.maxX, y + box.maxY, z + box.maxZ, sideColor, lineColor, shape, 0);
        }
    }

    // 2D

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (!hasRenderingMode(Mode._2D)) return;

        Renderer2D.COLOR.begin();

        for (Entity entity : mc.world.getEntities()) {
            @Nullable ESPEntityData entityData = getEntityData(entity);
            if (entityData == null || shouldSkip(entity) || entityData.mode.get() != Mode._2D) continue;

            Box box = entity.getBoundingBox();

            double x = MathHelper.lerp(event.tickDelta, entity.lastRenderX, entity.getX()) - entity.getX();
            double y = MathHelper.lerp(event.tickDelta, entity.lastRenderY, entity.getY()) - entity.getY();
            double z = MathHelper.lerp(event.tickDelta, entity.lastRenderZ, entity.getZ()) - entity.getZ();

            // Check corners
            pos1.set(Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE);
            pos2.set(0, 0, 0);

            //     Bottom
            if (checkCorner(box.minX + x, box.minY + y, box.minZ + z, pos1, pos2)) continue;
            if (checkCorner(box.maxX + x, box.minY + y, box.minZ + z, pos1, pos2)) continue;
            if (checkCorner(box.minX + x, box.minY + y, box.maxZ + z, pos1, pos2)) continue;
            if (checkCorner(box.maxX + x, box.minY + y, box.maxZ + z, pos1, pos2)) continue;

            //     Top
            if (checkCorner(box.minX + x, box.maxY + y, box.minZ + z, pos1, pos2)) continue;
            if (checkCorner(box.maxX + x, box.maxY + y, box.minZ + z, pos1, pos2)) continue;
            if (checkCorner(box.minX + x, box.maxY + y, box.maxZ + z, pos1, pos2)) continue;
            if (checkCorner(box.maxX + x, box.maxY + y, box.maxZ + z, pos1, pos2)) continue;

            // Setup color
            Color color = getColor(entityData, entity);
            if (color != null) {
                lineColor.set(color);
                sideColor.set(color).a((int) (sideColor.a * entityData.fillOpacity.get()));
            }

            // Render
            if (entityData.shapeMode.get() != ShapeMode.Lines && sideColor.a > 0) {
                Renderer2D.COLOR.quad(pos1.x, pos1.y, pos2.x - pos1.x, pos2.y - pos1.y, sideColor);
            }

            if (entityData.shapeMode.get() != ShapeMode.Sides) {
                Renderer2D.COLOR.line(pos1.x, pos1.y, pos1.x, pos2.y, lineColor);
                Renderer2D.COLOR.line(pos2.x, pos1.y, pos2.x, pos2.y, lineColor);
                Renderer2D.COLOR.line(pos1.x, pos1.y, pos2.x, pos1.y, lineColor);
                Renderer2D.COLOR.line(pos1.x, pos2.y, pos2.x, pos2.y, lineColor);
            }

            count++;
        }

        Renderer2D.COLOR.render();
    }

    public boolean forceRender(Entity entity) {
        @Nullable ESPEntityData entityData;
        return isActive() && (entityData = getEntityData(entity)) != null && (entityData.mode.get() == Mode.Shader || entityData.mode.get() == Mode.Glow);
    }

    private boolean checkCorner(double x, double y, double z, Vector3d min, Vector3d max) {
        pos.set(x, y, z);
        if (!NametagUtils.to2D(pos, 1)) return true;

        // Check Min
        if (pos.x < min.x) min.x = pos.x;
        if (pos.y < min.y) min.y = pos.y;
        if (pos.z < min.z) min.z = pos.z;

        // Check Max
        if (pos.x > max.x) max.x = pos.x;
        if (pos.y > max.y) max.y = pos.y;
        if (pos.z > max.z) max.z = pos.z;

        return false;
    }

    // Settings

    public @Nullable ESPEntityData getEntityData(Entity entity) {
        return entityDatas.apply(entity);
    }

    private boolean hasRenderingMode(Mode mode) {
        for (EntitySelectionDataSetting.SettingEntry<ESPEntityData> entry : entityDatas.get()) {
            if (entry.data().mode.get() == mode) {
                return true;
            }
        }

        return false;
    }

    // Utils

    public boolean drawAsTarget(Entity entity) {
        return highlightTarget.get() && mc.crosshairTarget instanceof EntityHitResult hr && hr.getEntity() == entity;
    }

    public boolean shouldSkip(Entity entity) {
        if (drawAsTarget(entity)) return false;
        if (!entityDatas.test(entity)) return true;
        if (entity == mc.player && ignoreSelf.get()) return true;
        if (entity == mc.getCameraEntity() && mc.options.getPerspective().isFirstPerson()) return true;
        return !EntityUtils.isInRenderDistance(entity);
    }

    public Color getColor(Entity entity) {
        return getColor(getEntityData(entity), entity);
    }

    public Color getColor(@Nullable ESPEntityData entityData, Entity entity) {
        Color color;
        double alpha = 1;

        if (drawAsTarget(entity)) {
            color = targetColor.get();
        } else {
            if (entityData == null) return null;

            alpha = getFadeAlpha(entityData, entity);
            if (alpha == 0) return null;

            color = getEntityTypeColor(entityData, entity);
        }

        return baseColor.set(color.r, color.g, color.b, (int) (color.a * alpha));
    }

    private double getFadeAlpha(ESPEntityData entityData, Entity entity) {
        double dist = PlayerUtils.squaredDistanceToCamera(entity.getX(), entity.getY() + entity.getEyeHeight(entity.getPose()), entity.getZ());
        double fadeDist = Math.pow(entityData.fadeDistance.get(), 2);
        double alpha = 1;
        if (dist <= fadeDist * fadeDist) alpha = (float) (Math.sqrt(dist) / fadeDist);
        if (alpha <= 0.075) alpha = 0;
        return alpha;
    }

    public Color getEntityTypeColor(ESPEntityData entityData, Entity entity) {
        if (entityData.colorMode.get() == ESPColorMode.Color) {
            Color color = entityData.color.get();
            return entity instanceof PlayerEntity player ? PlayerUtils.getPlayerColor(player, color) : color;
        }

        if (friendOverride.get() && entity instanceof PlayerEntity player
            && Friends.get().isFriend(player)) {
            return Config.get().friendColor.get();
        }

        if (entityData.colorMode.get() == ESPColorMode.Health) return EntityUtils.getColorFromHealth(entity, entityData.color.get());
        else return EntityUtils.getColorFromDistance(entity);
    }

    @Override
    public String getInfoString() {
        return Integer.toString(count);
    }

    public boolean isShader() {
        return isActive() && hasRenderingMode(Mode.Shader);
    }

    public boolean isGlow() {
        return isActive() && hasRenderingMode(Mode.Glow);
    }

    public enum ESPColorMode {
        Color,
        Distance,
        Health;
    }

    public enum Mode {
        Box,
        Wireframe,
        _2D,
        Shader,
        Glow;

        @Override
        public String toString() {
            return this == _2D ? "2D" : super.toString();
        }
    }

    public enum ShaderMode {
        Glow,
        Glow_Texture;


        @Override
        public String toString() {
            return this == Glow_Texture ? "Glow with Texture" : super.toString();
        }
    }

    public enum BlendMode {
        Darken,
        Lighten
    }
}
