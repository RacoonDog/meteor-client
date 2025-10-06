/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.settings;

import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.misc.IGetter;
import meteordevelopment.meteorclient.utils.misc.ISerializable;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

public abstract class Setting<T> implements IGetter<T>, ISerializable<T> {
    private static final List<String> NO_SUGGESTIONS = new ArrayList<>(0);

    public final String name, title, description;
    private final IVisible visible;

    protected final Supplier<T> defaultValueSupplier;
    protected T value;
    protected boolean changed;

    public final Consumer<Setting<T>> onModuleActivated;
    private final Consumer<T> onChanged;

    public Module module;
    public boolean lastWasVisible;

    public Setting(String name, String description, Supplier<T> defaultValue, Consumer<T> onChanged, Consumer<Setting<T>> onModuleActivated, IVisible visible) {
        this.name = name;
        this.title = Utils.nameToTitle(name);
        this.description = description;
        this.defaultValueSupplier = defaultValue;
        this.onChanged = onChanged;
        this.onModuleActivated = onModuleActivated;
        this.visible = visible;

        resetImpl();
    }

    // some setting types just have no use for dynamic defaults
    protected Setting(String name, String description, T defaultValue, Consumer<T> onChanged, Consumer<Setting<T>> onModuleActivated, IVisible visible) {
        this(name, description, () -> defaultValue, onChanged, onModuleActivated, visible);
    }

    @Override
    public T get() {
        return value;
    }

    public boolean set(T value) {
        if (!isValueValid(value)) return false;
        this.value = value;
        if (value != getDefaultValue()) this.changed = true;
        onChanged();
        return true;
    }

    protected void resetImpl() {
        value = getDefaultValue();
    }

    public void reset() {
        resetImpl();
        changed = false;
        onChanged();
    }

    public void updateDefaults() {
        if (!changed) this.reset();
    }

    public T getDefaultValue() {
        return defaultValueSupplier.get();
    }

    public boolean parse(String str) {
        T newValue = parseImpl(str);

        if (newValue != null) {
            if (isValueValid(newValue)) {
                value = newValue;
                onChanged();
            }
        }

        return newValue != null;
    }

    public boolean wasChanged() {
        return changed;
    }

    public void onChanged() {
        if (onChanged != null) onChanged.accept(value);
    }

    public void onActivated() {
        if (onModuleActivated != null) onModuleActivated.accept(this);
    }

    public boolean isVisible() {
        return visible == null || visible.isVisible();
    }

    protected abstract T parseImpl(String str);

    protected abstract boolean isValueValid(T value);

    public Iterable<Identifier> getIdentifierSuggestions() {
        return null;
    }

    public List<String> getSuggestions() {
        return NO_SUGGESTIONS;
    }

    protected abstract NbtCompound save(NbtCompound tag);

    @Override
    public NbtCompound toTag() {
        NbtCompound tag = new NbtCompound();

        tag.putString("name", name);
        tag.putBoolean("changed", changed);
        save(tag);

        return tag;
    }

    protected abstract T load(NbtCompound tag);

    @Override
    public T fromTag(NbtCompound tag) {
        T value = load(tag);
        changed = tag.getBoolean("changed", false); // todo detect manual file modification and update 'changed' accordingly
        onChanged();

        return value;
    }

    @Override
    public String toString() {
        return value.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Setting<?> setting = (Setting<?>) o;
        return Objects.equals(name, setting.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Nullable
    public static <T> T parseId(Registry<T> registry, String name) {
        name = name.trim();

        Identifier id;
        if (name.contains(":")) id = Identifier.of(name);
        else id = Identifier.of("minecraft", name);
        if (registry.containsId(id)) return registry.get(id);

        return null;
    }

    @SuppressWarnings("unchecked")
    public abstract static class SettingBuilder<B, V, S> {
        protected String name = "undefined", description = "";
        protected V defaultValue;
        protected Supplier<V> defaultValueSupplier;
        protected IVisible visible;
        protected Consumer<V> onChanged;
        protected Consumer<Setting<V>> onModuleActivated;

        protected SettingBuilder(V defaultValue) {
            this.defaultValue = defaultValue;
            this.defaultValueSupplier = () -> defaultValue;
        }

        public B name(String name) {
            this.name = name;
            return (B) this;
        }

        public B description(String description) {
            this.description = description;
            return (B) this;
        }

        public B defaultValue(V defaultValue) {
            this.defaultValue = defaultValue;
            this.defaultValueSupplier = () -> defaultValue;
            return (B) this;
        }

        public B defaultValue(Supplier<V> defaultValue) {
            this.defaultValue = defaultValue.get();
            this.defaultValueSupplier = defaultValue;
            return (B) this;
        }

        public B visible(IVisible visible) {
            this.visible = visible;
            return (B) this;
        }

        public B onChanged(Consumer<V> onChanged) {
            this.onChanged = onChanged;
            return (B) this;
        }

        public B onModuleActivated(Consumer<Setting<V>> onModuleActivated) {
            this.onModuleActivated = onModuleActivated;
            return (B) this;
        }

        public abstract S build();
    }
}
