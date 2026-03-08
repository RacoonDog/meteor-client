/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.render.esp;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WidgetScreen;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.pressable.WCheckbox;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.utils.misc.ICopyable;
import meteordevelopment.meteorclient.utils.misc.ISerializable;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;

public class ESPEntityData implements ICopyable<ESPEntityData>, ISerializable<ESPEntityData>, IEntityData<ESPEntityData>, IGeneric<ESPEntityData> {
    public final Settings settings = new Settings();

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgColors = settings.createGroup("Colors");

    // General

    public final Setting<ESP.Mode> mode = sgGeneral.add(new EnumSetting.Builder<ESP.Mode>()
        .name("mode")
        .description("Rendering mode.")
        .defaultValue(ESP.Mode.Shader)
        .build()
    );

    public final Setting<ESP.ShaderMode> shaderMode = sgGeneral.add(new EnumSetting.Builder<ESP.ShaderMode>()
        .name("shader-mode")
        .description("What kind of shader to use.")
        .defaultValue(ESP.ShaderMode.Glow)
        .visible(() -> mode.get() == ESP.Mode.Shader)
        .build()
    );

    public final Setting<ESP.BlendMode> colorBlendMode = sgGeneral.add(new EnumSetting.Builder<ESP.BlendMode>()
        .name("color-blend-mode")
        .description("How to blend colors.")
        .defaultValue(ESP.BlendMode.Lighten)
        .visible(() -> mode.get() == ESP.Mode.Shader && shaderMode.get() == ESP.ShaderMode.Glow_Texture)
        .build()
    );

    public final Setting<Integer> outlineWidth = sgGeneral.add(new IntSetting.Builder()
        .name("outline-width")
        .description("The width of the shader outline.")
        .visible(() -> mode.get() == ESP.Mode.Shader)
        .defaultValue(5)
        //.range(1, 20)
        .min(1)
        .sliderRange(1, 10)
        .build()
    );

    public final Setting<Double> glowMultiplier = sgGeneral.add(new DoubleSetting.Builder()
        .name("glow-multiplier")
        .description("Multiplier for glow effect")
        .visible(() -> mode.get() == ESP.Mode.Shader)
        .decimalPlaces(3)
        .defaultValue(1.5)
        .min(0)
        .sliderMax(10)
        .build()
    );

    public final Setting<ShapeMode> shapeMode = sgGeneral.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How the shapes are rendered.")
        .visible(() -> mode.get() != ESP.Mode.Glow)
        .defaultValue(ShapeMode.Both)
        .build()
    );

    public final Setting<Double> fillOpacity = sgGeneral.add(new DoubleSetting.Builder()
        .name("fill-opacity")
        .description("The opacity of the shape fill.")
        .visible(() -> shapeMode.get() != ShapeMode.Lines && mode.get() != ESP.Mode.Glow)
        .defaultValue(0.3)
        .range(0, 1)
        .sliderMax(1)
        .build()
    );

    public final Setting<Double> fadeDistance = sgGeneral.add(new DoubleSetting.Builder()
        .name("fade-distance")
        .description("The distance from an entity where the color begins to fade.")
        .defaultValue(3)
        .min(0)
        .sliderMax(12)
        .build()
    );

    // Colors

    public final Setting<ESP.ESPColorMode> colorMode = sgColors.add(new EnumSetting.Builder<ESP.ESPColorMode>()
        .name("color-mode")
        .description("Determines the colors used for entities.")
        .defaultValue(ESP.ESPColorMode.Color)
        .build()
    );

    public final Setting<SettingColor> color = sgColors.add(new ColorSetting.Builder()
        .name("color")
        .description("The color.")
        .defaultValue(new SettingColor(255, 255, 255))
        .visible(() -> colorMode.get() == ESP.ESPColorMode.Color)
        .build()
    );

    public boolean enabled = true;

    @Override
    public boolean isValid() {
        return this.enabled;
    }

    @Override
    public NbtCompound toTag() {
        NbtCompound compound = new NbtCompound();
        compound.put("settings", this.settings.toTag());
        compound.putBoolean("enabled", this.enabled);
        return compound;
    }

    @Override
    public ESPEntityData fromTag(NbtCompound tag) {
        tag.getCompound("settings").ifPresent(this.settings::fromTag);
        this.enabled = tag.getBoolean("enabled").orElse(true);
        return this;
    }

    @Override
    public WidgetScreen createScreen(GuiTheme theme, EntitySelection selection, EntitySelectionDataSetting<ESPEntityData> setting) {
        return new ESPEntityDataScreen(theme, this, selection, setting);
    }

    @Override
    public WidgetScreen createScreen(GuiTheme theme, GenericSetting<ESPEntityData> setting) {
        return new ESPEntityDataScreen(theme, this, setting);
    }

    @Override
    public void addWidgets(GuiTheme theme, WTable table, EntitySelection selection, EntitySelectionDataSetting<ESPEntityData> setting) {
        WCheckbox enabled = table.add(theme.checkbox(this.enabled)).expandCellX().widget();
        enabled.action = () -> this.enabled = enabled.checked;

        table.add(switch (colorMode.get()) {
            case Color -> theme.quad(color.get());
            case Health -> theme.item(Items.GOLDEN_APPLE.getDefaultStack());
            case Distance -> theme.item(Items.COMPASS.getDefaultStack());
        }).expandCellX();
    }

    @Override
    public ESPEntityData set(ESPEntityData value) {
        this.settings.fromTag(value.settings.toTag());
        return this;
    }

    @Override
    public ESPEntityData copy() {
        ESPEntityData newData = new ESPEntityData();
        newData.settings.fromTag(this.settings.toTag());
        return newData;
    }
}
