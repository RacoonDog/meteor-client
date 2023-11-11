/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.reflection;

import meteordevelopment.meteorclient.reflection.ReflectInit;

import java.util.function.Consumer;

/**
 * Indicates that this subtype can be registered via {@link ReflectInit#initRegisterable(Class, Consumer)}
 */
public interface IRegisterable {
}
