/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.render.marker;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public class MarkerFactory {
    @FunctionalInterface
    public interface Factory {
        BaseMarker create();
    }

    private final Map<String, Factory> factories = new Object2ObjectOpenHashMap<>();
    private final List<String> names = new ObjectArrayList<>();

    public MarkerFactory() {
        registerMarkerType(CuboidMarker.type, CuboidMarker::new);
        registerMarkerType(Sphere2dMarker.type, Sphere2dMarker::new);
        registerMarkerType(Sphere3dMarker.type, Sphere3dMarker::new);
        registerMarkerType(CylinderMarker.type, CylinderMarker::new);
    }

    /**
     * Registers a {@link BaseMarker} to be usable from within the Marker module.
     *
     * @param type the human-readable name associated with the marker type. Must be unique.
     * @param factory the supplier that returns a unique instance of a {@link BaseMarker}.
     */
    public void registerMarkerType(String type, Factory factory) {
        @Nullable Factory old = factories.put(type, factory);
        if (old != null) throw new IllegalStateException("Duplicate Marker type registered for '%s'".formatted(type));
        names.add(type);
    }

    public String[] getNames() {
        return names.toArray(new String[0]);
    }

    public BaseMarker createMarker(String name) {
        if (factories.containsKey(name)) {
            BaseMarker marker = factories.get(name).create();
            marker.settings.registerColorSettings(Modules.get().get(Marker.class));

            return marker;
        }

        return null;
    }
}
