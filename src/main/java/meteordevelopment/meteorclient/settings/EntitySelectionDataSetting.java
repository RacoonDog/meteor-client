/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.settings;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.screens.settings.EntityTypeListSettingScreen;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.gui.widgets.pressable.WMinus;
import meteordevelopment.meteorclient.utils.misc.ICopyable;
import meteordevelopment.meteorclient.utils.misc.IGetter;
import meteordevelopment.meteorclient.utils.misc.ISerializable;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.random.LocalRandom;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public class EntitySelectionDataSetting<T extends ICopyable<T> & ISerializable<T> & IEntityData<T>> extends Setting<List<EntitySelectionDataSetting.SettingEntry<T>>> implements Predicate<Entity>, Function<Entity, T> {
    public final IGetter<T> defaultData;

    public EntitySelectionDataSetting(String name, String description, List<SettingEntry<T>> defaultValue, Consumer<List<SettingEntry<T>>> onChanged, Consumer<Setting<List<SettingEntry<T>>>> onModuleActivated, IVisible visible, IGetter<T> defaultData) {
        super(name, description, defaultValue, onChanged, onModuleActivated, visible);

        this.defaultData = defaultData;
    }

    @Override
    protected void resetImpl() {
        this.value = new ObjectArrayList<>(this.defaultValue);
    }

    @Override
    protected List<SettingEntry<T>> parseImpl(String str) {
        return new ObjectArrayList<>();
    }

    @Override
    protected boolean isValueValid(List<SettingEntry<T>> value) {
        return true;
    }

    @Override
    protected NbtCompound save(NbtCompound tag) {
        NbtList entriesTag = new NbtList();
        for (SettingEntry<T> entry : this.get()) {
            NbtCompound entryTag = new NbtCompound();
            entryTag.put("selection", entry.selection().toTag());
            entryTag.put("data", entry.data.toTag());

            entriesTag.add(entryTag);
        }

        tag.put("entries", entriesTag);

        return tag;
    }

    @Override
    protected List<SettingEntry<T>> load(NbtCompound tag) {
        List<SettingEntry<T>> values = new ObjectArrayList<>();

        NbtList entriesTag = tag.getListOrEmpty("entries");
        for (NbtElement element : entriesTag) {
            if (element instanceof NbtCompound compound) {
                Optional<NbtCompound> selectionTag = compound.getCompound("selection");
                Optional<NbtCompound> dataTag = compound.getCompound("data");
                if (selectionTag.isPresent() && dataTag.isPresent()) {
                    values.add(new SettingEntry<>(
                        new EntitySelection().fromTag(selectionTag.get()),
                        this.defaultData.get().copy().fromTag(dataTag.get())
                    ));
                }
            }
        }

        return values;
    }

    @Nullable
    @Override
    public T apply(Entity entity) {
        for (SettingEntry<T> entry : this.get()) {
            if (entry.selection().test(entity)) {
                return entry.data();
            }
        }

        return null;
    }

    @Override
    public boolean test(Entity entity) {
        for (SettingEntry<T> entry : this.get()) {
            if (entry.selection().test(entity)) {
                return true;
            }
        }

        return false;
    }

    public static <T extends ICopyable<T> & ISerializable<T> & IEntityData<T>> void fillTable(GuiTheme theme, WTable table, EntitySelectionDataSetting<T> setting) {
        table.clear();

        for (EntitySelectionDataSetting.SettingEntry<T> entry : setting.get()) {
            WMinus delete = table.add(theme.minus()).widget();
            delete.action = () -> {
                setting.get().remove(entry);
                fillTable(theme, table, setting);
            };

            WButton edit = table.add(theme.button(GuiRenderer.EDIT)).expandWidgetX().widget();
            edit.action = () -> {
                EntityTypeListSetting tempSetting = new EntityTypeListSetting.Builder()
                    .name("entities")
                    .description("Which entities to render in the selection.")
                    .defaultValue(Set.of())
                    .onModuleActivated(s -> {
                        s.get().clear();
                        s.get().addAll(entry.selection().entityTypes);
                    })
                    .onChanged(set -> {
                        entry.selection().entityTypes.clear();
                        entry.selection().entityTypes.addAll(set);
                    })
                    .build();

                tempSetting.onActivated();

                MinecraftClient.getInstance().setScreen(new EntityTypeListSettingScreen(theme, tempSetting));
                fillTable(theme, table, setting);
            };

            table.add(theme.item(Registries.ITEM.getRandom(new LocalRandom(ThreadLocalRandom.current().nextLong())).get().value().getDefaultStack()));

            @Nullable WWidget widget = entry.data().getWidget(theme, entry.selection(), setting);
            if (widget != null) {
                table.add(widget).right();
            }

            WButton config = table.add(theme.button("Config")).expandWidgetX().widget();
            config.action = () -> {
                MinecraftClient.getInstance().setScreen(entry.data().createScreen(theme, entry.selection(), setting));
                fillTable(theme, table, setting);
            };

            table.row();
        }

        if (!setting.get().isEmpty()) {
            table.add(theme.horizontalSeparator()).expandX();
            table.row();
        }

        WButton add = table.add(theme.button("Add")).expandX().widget();
        add.action = () -> {
            setting.get().add(new SettingEntry<>(
                new EntitySelection(),
                setting.defaultData.get().copy()
            ));
            fillTable(theme, table, setting);
        };

        WButton reset = table.add(theme.button(GuiRenderer.RESET)).widget();
        reset.action = () -> {
            setting.reset();
            fillTable(theme, table, setting);
        };
        reset.tooltip = "Reset";
    }

    public record SettingEntry<T extends ICopyable<T> & ISerializable<T> & IEntityData<T>>(EntitySelection selection, T data) {}

    public static class Builder<T extends ICopyable<T> & ISerializable<T> & IEntityData<T>> extends SettingBuilder<Builder<T>, List<EntitySelectionDataSetting.SettingEntry<T>>, EntitySelectionDataSetting<T>> {
        private IGetter<T> defaultData;

        public Builder() {
            super(new ObjectArrayList<>());
        }

        public Builder<T> defaultData(IGetter<T> defaultData) {
            this.defaultData = defaultData;
            return this;
        }

        @Override
        public EntitySelectionDataSetting<T> build() {
            if (this.defaultData == null) throw new IllegalArgumentException("defaultData cannot be null.");
            return new EntitySelectionDataSetting<>(name, description, defaultValue, onChanged, onModuleActivated, visible, defaultData);
        }
    }
}
