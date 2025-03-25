/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.settings;

import com.mojang.brigadier.StringReader;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.utils.misc.Names;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.*;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class BlockStateListSetting extends Setting<List<BlockStateListSetting.StateEntry>> {
    public final List<StateEntry> universe;

    public BlockStateListSetting(String name, String description, List<StateEntry> defaultValue, Consumer<List<StateEntry>> onChanged, Consumer<Setting<List<StateEntry>>> onModuleActivated, IVisible visible, List<StateEntry> universe) {
        super(name, description, defaultValue, onChanged, onModuleActivated, visible);

        this.universe = universe;
    }

    @Override
    protected void resetImpl() {
        this.value = new ObjectArrayList<>(this.defaultValue);
    }

    @Override
    protected List<StateEntry> parseImpl(String str) {
        try {
            return deserialize((NbtList) new StringNbtReader(new StringReader(str)).parseElement(), false);
        } catch (Throwable t) {
            return null;
        }
    }

    @Override
    protected boolean isValueValid(List<StateEntry> value) {
        return true;
    }

    @Override
    protected NbtCompound save(NbtCompound tag) {
        NbtList list = new NbtList();
        for (StateEntry entry : get()) {
            list.add(entry.serialize());
        }

        tag.put("value", list);

        return tag;
    }

    @Override
    protected List<StateEntry> load(NbtCompound tag) {
        get().clear();

        NbtList list = tag.getList("value", NbtElement.COMPOUND_TYPE);
        get().addAll(deserialize(list, true));

        return get();
    }

    protected List<StateEntry> deserialize(NbtList nbtList, boolean shouldWarn) {
        List<StateEntry> list = new ObjectArrayList<>(nbtList.size());
        for (NbtElement element : nbtList) {
            try {
                list.add(StateEntry.deserialize((NbtCompound) element));
            } catch (Throwable t) {
                if (shouldWarn) {
                    if (element instanceof NbtCompound compound && compound.contains("block", NbtElement.STRING_TYPE)) {
                        MeteorClient.LOG.warn("Could not deserialize value '{}' for setting '{}'.", compound.getString("block"), this.name);
                    } else {
                        MeteorClient.LOG.warn("Could not deserialize value for setting '{}'.", this.name);
                    }
                }
            }
        }
        return list;
    }

    public static class Builder extends SettingBuilder<Builder, List<StateEntry>, BlockStateListSetting> {
        private final List<StateEntry> DEFAULT_UNIVERSE = Registries.BLOCK.stream().map(StateEntry::allOf).toList();
        private Set<StateEntry> universe = new ObjectLinkedOpenHashSet<>(DEFAULT_UNIVERSE);

        public Builder() {
            super(List.of());
        }

        @Override
        public Builder defaultValue(List<StateEntry> defaultValue) {
            universe.addAll(defaultValue);
            return super.defaultValue(defaultValue);
        }

        public Builder defaultValue(Block... defaults) {
            return defaultValue(defaults != null ? Arrays.stream(defaults).map(StateEntry::allOf).toList() : List.of());
        }

        public Builder addToDefaults(StateEntry entry) {
            if (this.defaultValue instanceof ObjectArrayList<StateEntry>) {
                this.defaultValue.add(entry);
            } else {
                this.defaultValue = new ObjectArrayList<>(this.defaultValue);
                this.defaultValue.add(entry);
            }
            return addToUniverse(entry);
        }

        public Builder addToUniverse(StateEntry entry) {
            this.universe.add(entry);
            return this;
        }

        public Builder removeFromUniverse(StateEntry entry) {
            this.universe.remove(entry);
            return this;
        }

        public Builder filterBlock(Predicate<Block> filter) {
            this.universe = new ObjectLinkedOpenHashSet<>(this.universe.stream().filter(entry -> filter.test(entry.block())).collect(Collectors.toList()));
            return this;
        }

        @Override
        public BlockStateListSetting build() {
            return new BlockStateListSetting(name, description, defaultValue, onChanged, onModuleActivated, visible, Collections.unmodifiableList(new ObjectArrayList<>(this.universe)));
        }
    }

    public record StateEntry(Block block, List<PropertyEntry<?>> properties, Supplier<String> displayName) {
        public StateEntry(Block block, List<PropertyEntry<?>> properties) {
            this(block, properties, () -> {
                String name = Names.get(block);
                if (!properties.isEmpty()) {
                    name += properties.stream()
                        .map(PropertyEntry::toString)
                        .collect(Collectors.joining(",", "[", "]"));
                }
                return name;
            });
        }

        public static StateEntry allOf(Block block) {
            return new StateEntry(block, List.of());
        }

        public static <T extends Comparable<T>> StateEntry only(Block block, Property<T> property, T value) {
            return new StateEntry(block, List.of(new PropertyEntry<>(property, value)));
        }

        public static <T extends Comparable<T>> StateEntry only(Block block, Property<T> property, T value, String overrideName) {
            return new StateEntry(block, List.of(new PropertyEntry<>(property, value)), () -> overrideName);
        }

        public BlockState getDisplayState() {
            BlockState state = block().getDefaultState();
            for (PropertyEntry<?> property : properties()) {
                state = property.apply(state);
            }
            return state;
        }

        public boolean matches(BlockState state) {
            if (state.getBlock() != block()) return false;

            for (PropertyEntry<?> entry : properties()) {
                if (!state.get(entry.property()).equals(entry.value())) return false;
            }

            return true;
        }

        public NbtCompound serialize() {
            NbtCompound nbt = new NbtCompound();
            nbt.putString("block", Registries.BLOCK.getId(this.block()).toString());

            if (!properties().isEmpty()) {
                NbtList propertyList = new NbtList();
                for (PropertyEntry<?> entry : properties()) {
                    propertyList.add(entry.serialize());
                }
                nbt.put("properties", propertyList);
            }

            return nbt;
        }

        public static StateEntry deserialize(NbtCompound nbt) {
            Block block = Registries.BLOCK.get(Identifier.of(nbt.getString("block")));

            if (nbt.contains("properties", NbtElement.LIST_TYPE)) {
                ObjectArrayList<PropertyEntry<?>> properties = new ObjectArrayList<>();
                for (NbtElement element : nbt.getList("properties", NbtElement.COMPOUND_TYPE)) {
                    NbtCompound compound = (NbtCompound) element;
                    String propertyName = compound.getString("property");

                    Property<?> property = block.getStateManager().getProperty(propertyName);
                    properties.add(PropertyEntry.deserialize(property, compound.get("value")));
                }

                properties.trim();
                return new StateEntry(block, properties);
            } else {
                return StateEntry.allOf(block);
            }
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof StateEntry other)) return false;
            if (block() != other.block()) return false;

            return this.properties().size() == other.properties().size() && new ObjectOpenHashSet<>(this.properties()).containsAll(other.properties());
        }

        @Override
        public String toString() {
            return displayName().get();
        }
    }

    public record PropertyEntry<T extends Comparable<T>>(Property<T> property, T value) {
        public BlockState apply(BlockState state) {
            return state.with(property(), value());
        }

        public NbtCompound serialize() {
            NbtCompound nbt = new NbtCompound();

            nbt.putString("property", property().getName());

            property().getCodec().encodeStart(NbtOps.INSTANCE, value).ifSuccess(nbtElement -> {
                nbt.put("value", nbtElement);
            }).getOrThrow();

            return nbt;
        }

        public static <T extends Comparable<T>> PropertyEntry<T> deserialize(Property<T> property, NbtElement nbt) {
            T value = property.getCodec().decode(NbtOps.INSTANCE, nbt).getOrThrow().getFirst();
            return new PropertyEntry<>(property, value);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof PropertyEntry<?> other)) return false;
            return property() == other.property() && value().equals(other.value());
        }

        @Override
        public String toString() {
            return property().getName() + "=" + property().name(value());
        }
    }
}
