/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.settings;

import java.util.function.Consumer;

public abstract class AbstractSetting<T> extends Setting<T> {
    protected final T defaultValue;
    protected T value;

    public AbstractSetting(String name, String description, T defaultValue, Consumer<T> onChanged, Consumer<Setting<T>> onModuleActivated, IVisible visible) {
        super(name, description, onChanged, onModuleActivated, visible);

        this.defaultValue = defaultValue;

        resetImpl();
    }

    @Override
    protected void setImpl(T value) {
        this.value = value;
    }

    @Override
    public T getDefaultValue() {
        return defaultValue;
    }

    @Override
    public T get() {
        return value;
    }
}
