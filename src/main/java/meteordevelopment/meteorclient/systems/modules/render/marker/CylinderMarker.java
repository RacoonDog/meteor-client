/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.render.marker;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.utils.network.MeteorExecutor;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.Dir;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.Future;

public class CylinderMarker extends BaseMarker {
    public static final String type = "Cylinder";

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<BlockPos> center = sgGeneral.add(new BlockPosSetting.Builder()
        .name("center")
        .description("Center of the sphere")
        .build()
    );

    private final Setting<Integer> radius = sgGeneral.add(new IntSetting.Builder()
        .name("radius")
        .description("Radius of the sphere")
        .defaultValue(20)
        .min(1)
        .noSlider()
        .onChanged(r -> dirty = true)
        .build()
    );

    private final Setting<Integer> height = sgGeneral.add(new IntSetting.Builder()
        .name("height")
        .description("The height of the cylinder")
        .defaultValue(10)
        .min(1)
        .sliderRange(1, 20)
        .build()
    );

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("What mode to use for this marker.")
        .defaultValue(Mode.Hollow)
        .onChanged(r -> dirty = true)
        .build()
    );

    // Render

    private final Setting<Boolean> limitRenderRange = sgRender.add(new BoolSetting.Builder()
        .name("limit-render-range")
        .description("Whether to limit rendering range (useful in very large circles)")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> renderRange = sgRender.add(new IntSetting.Builder()
        .name("render-range")
        .description("Rendering range")
        .defaultValue(10)
        .min(1)
        .sliderRange(1, 20)
        .visible(limitRenderRange::get)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How the shapes are rendered.")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("side-color")
        .description("The color of the sides of the blocks being rendered.")
        .defaultValue(new SettingColor(0, 100, 255, 50))
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color")
        .description("The color of the lines of the blocks being rendered.")
        .defaultValue(new SettingColor(0, 100, 255, 255))
        .build()
    );

    private volatile List<RenderBlock> blocks = List.of();
    private volatile @Nullable Future<?> task = null;
    private boolean dirty = true;

    public CylinderMarker() {
        super(type);
    }

    @Override
    protected void render(Render3DEvent event) {
        if (dirty) {
            dirty = false;
            if (task != null) task.cancel(true);

            Runnable action = () -> {
                calcCircle();
                task = null;
            };

            if (radius.get() <= 50) action.run();
            else {
                task = MeteorExecutor.executeFuture(action);
            }
        }

        BlockPos origin = center.get();

        for (RenderBlock block : blocks) {
            if (!limitRenderRange.get() || PlayerUtils.isWithin(block.x, block.y, block.z, renderRange.get())) {
                event.renderer.box(origin.getX() + block.x, origin.getY() + block.y, origin.getZ() + block.z, origin.getX() + block.x + 1, origin.getY() + block.y + height.get(), origin.getZ() + block.z + 1, sideColor.get(), lineColor.get(), shapeMode.get(), block.excludeDir);
            }
        }
    }

    @Override
    public String getTypeName() {
        return type;
    }

    private void calcCircle() {
        int r = radius.get();

        // this is entirely arbitrary but always slightly bigger than the actual count -crosby
        int size = MathHelper.ceil(10.4 * r * r);

        ObjectArrayList<RenderBlock> renderBlocks = new ObjectArrayList<>(size);
        boolean hollow = mode.get() == Mode.Hollow;

        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                if (!isInside(x, z, r)) continue;

                byte excludeDir = getExcludeDir(x, z, r);

                if (!hollow && excludeDir == 0b1111000) continue;

                if (hollow) {
                    excludeDir = 0;

                    for (Direction dir : Direction.HORIZONTAL) {
                        int x2 = x + dir.getOffsetX();
                        int z2 = z + dir.getOffsetZ();

                        if (isInside(x2, z2, r) && getExcludeDir(x2, z2, r) != 0b1111000)
                            excludeDir |= Dir.get(dir);
                    }
                }

                renderBlocks.add(new RenderBlock(x, 0, z, excludeDir));
            }
        }

        renderBlocks.trim();
        blocks = renderBlocks;
    }

    private byte getExcludeDir(int x, int z, int r) {
        byte excludeDir = 0;

        for (Direction dir : Direction.HORIZONTAL) {
            if (isInside(x + dir.getOffsetX(), z + dir.getOffsetZ(), r))
                excludeDir |= Dir.get(dir);
        }

        return excludeDir;
    }

    private boolean isInside(int x, int z, int r) {
        return (x * x + z * z) <= (r * r);
    }
}
