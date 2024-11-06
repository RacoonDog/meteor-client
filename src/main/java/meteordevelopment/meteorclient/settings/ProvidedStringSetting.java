/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.settings;

import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import net.minecraft.nbt.NbtCompound;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Dropdown-like setting (similar to {@link EnumSetting}) but with a dynamic list of possible choices.
 */
public class ProvidedStringSetting extends StringSetting {
    public final Supplier<String[]> supplier;

    public ProvidedStringSetting(String name, String description, String defaultValue, Consumer<String> onChanged, Consumer<Setting<String>> onModuleActivated, IVisible visible, Class<? extends WTextBox.Renderer> renderer, boolean wide, Supplier<String[]> supplier) {
        super(name, description, defaultValue, onChanged, onModuleActivated, visible, renderer, null, wide);

        this.supplier = supplier;
    }

    @Override
    protected boolean isValueValid(String value) {
        for (String possibleValue : supplier.get()) {
            if (value.equals(possibleValue)) return true;
        }
        return false;
    }

    @Override
    public List<String> getSuggestions() {
        return List.of(supplier.get());
    }

    @Override
    public String load(NbtCompound tag) {
        String value = tag.getString("value");

        if (isValueValid(value)) {
            set(value);
        }

        return get();
    }

    public static class Builder extends SettingBuilder<Builder, String, ProvidedStringSetting> {
        private Class<? extends WTextBox.Renderer> renderer;
        private Supplier<String[]> supplier;
        private boolean wide;

        public Builder() {
            super(null);
        }

        public Builder renderer(Class<? extends WTextBox.Renderer> renderer) {
            this.renderer = renderer;
            return this;
        }

        public Builder supplier(Supplier<String[]> supplier) {
            this.supplier = supplier;
            return this;
        }

        public Builder wide() {
            wide = true;
            return this;
        }

        @Override
        public ProvidedStringSetting build() {
            Objects.requireNonNull(defaultValue, "Default value cannot be null");
            return new ProvidedStringSetting(name, description, defaultValue, onChanged, onModuleActivated, visible, renderer, wide, supplier);
        }
    }
}
