/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.settings;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.widgets.containers.WContainer;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.utils.misc.ISerializable;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifierSlot;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.Tameable;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.GameMode;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.StreamSupport;

public class EntitySelection implements ISerializable<EntitySelection>, Predicate<Entity> {
    public final Settings settings = new Settings();

    public final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    public final SettingGroup sgFilters = this.settings.createGroup("Filters");
    public final SettingGroup sgEquipment = this.settings.createGroup("Equipment");

    // General

    public final Setting<String> name = sgGeneral.add(new StringSetting.Builder()
        .name("name")
        .description("What to name this selection in the gui.")
        .defaultValue("")
        .build()
    );

    public final Setting<Set<EntityType<?>>> entityTypes = sgGeneral.add(new EntityTypeListSetting.Builder()
        .name("entities")
        .description("What entity types to select.")
        .build()
    );

    // Filters

    public final Setting<Filter> invisibles = sgFilters.add(new EnumSetting.Builder<Filter>()
        .name("invisibles")
        .description("How to filter invisible entities.")
        .defaultValue(Filter.Nothing)
        .build()
    );

    public final Setting<Filter> markers = sgFilters.add(new EnumSetting.Builder<Filter>()
        .name("markers")
        .description("How to filter marker armor stand entities.")
        .defaultValue(Filter.Nothing)
        .visible(() -> entityTypes.get().contains(EntityType.ARMOR_STAND))
        .build()
    );

    public final Setting<Filter> babies = sgFilters.add(new EnumSetting.Builder<Filter>()
        .name("babies")
        .description("How to filter baby entities.")
        .defaultValue(Filter.Nothing)
        .build()
    );

    public final Setting<Filter> friends = sgFilters.add(new EnumSetting.Builder<Filter>()
        .name("friends")
        .description("How to filter friend players.")
        .defaultValue(Filter.Nothing)
        .visible(() -> entityTypes.get().contains(EntityType.PLAYER))
        .build()
    );

    public final Setting<Boolean> filterGamemode = sgFilters.add(new BoolSetting.Builder()
        .name("filter-gamemode")
        .description("Whether to filter players by gamemode.")
        .defaultValue(false)
        .visible(() -> entityTypes.get().contains(EntityType.PLAYER))
        .build()
    );

    public final Setting<GameMode> gamemode = sgFilters.add(new EnumSetting.Builder<GameMode>()
        .name("gamemode")
        .description("What gamemode to filter by.")
        .defaultValue(GameMode.DEFAULT)
        .visible(() -> entityTypes.get().contains(EntityType.PLAYER) && filterGamemode.get())
        .build()
    );

    public final Setting<Filter> hostiles = sgFilters.add(new EnumSetting.Builder<Filter>()
        .name("hostiles")
        .description("How to filter hostile entities.")
        .defaultValue(Filter.Nothing)
        .build()
    );

    public final Setting<Filter> named = sgFilters.add(new EnumSetting.Builder<Filter>()
        .name("named")
        .description("How to filter named entities.")
        .defaultValue(Filter.Nothing)
        .build()
    );

    public final Setting<Filter> tamed = sgFilters.add(new EnumSetting.Builder<Filter>()
        .name("tamed")
        .description("How to filter tamed entities.")
        .defaultValue(Filter.Nothing)
        .build()
    );

    // Equipement

    public final Setting<Boolean> filterEquipment = sgEquipment.add(new BoolSetting.Builder()
        .name("filter-equipment")
        .description("Whether to filter entities by their worn equipment.")
        .defaultValue(false)
        .build()
    );

    public final Setting<List<Item>> equipment = sgEquipment.add(new ItemListSetting.Builder()
        .name("equipment")
        .description("What equipment to filter by.")
        .filter(item -> item.getComponents().contains(DataComponentTypes.EQUIPPABLE))
        .visible(this.filterEquipment::get)
        .build()
    );

    public final Setting<Boolean> filterHeldItems = sgEquipment.add(new BoolSetting.Builder()
        .name("filter-held-items")
        .description("Whether to filter entities by their held items.")
        .defaultValue(false)
        .build()
    );

    public final Setting<List<Item>> heldItems = sgEquipment.add(new ItemListSetting.Builder()
        .name("help-items")
        .description("What held items to filter by.")
        .visible(this.filterHeldItems::get)
        .build()
    );

    // errors? what errors? i dont see no damn errors
    @Override
    public boolean test(Entity entity) {
        return this.entityTypes.get().contains(entity.getType())
            && this.invisibles.get().include(entity.isInvisible())
            && (!(entity instanceof ArmorStandEntity armorStand) || this.markers.get().include(armorStand.isMarker()))
            && (!(entity instanceof LivingEntity livingEntity) || this.babies.get().include(livingEntity.isBaby()))
            && (!(entity instanceof PlayerEntity playerEntity) || this.friends.get().include(Friends.get().isFriend(playerEntity)))
            && (!(entity instanceof PlayerEntity playerEntity) || !this.filterGamemode.get() || playerEntity.getGameMode() == this.gamemode.get())
            && (!(entity instanceof LivingEntity) || this.hostiles.get().include(entity instanceof MobEntity mobEntity && mobEntity.isAttacking() || entity instanceof EndermanEntity enderman && enderman.isAngry()))
            && this.named.get().include(entity.hasCustomName())
            && (!(entity instanceof Tameable tameable) || this.tamed.get().include(tameable.getOwnerReference() != null))
            && (!(entity instanceof LivingEntity livingEntity) || !this.filterEquipment.get() || StreamSupport.stream(AttributeModifierSlot.ARMOR.spliterator(), false)
                .map(livingEntity::getEquippedStack).map(ItemStack::getItem).anyMatch(equipment.get()::contains))
            && (!(entity instanceof LivingEntity livingEntity) || !this.filterHeldItems.get() || StreamSupport.stream(AttributeModifierSlot.HAND.spliterator(), false)
            .map(livingEntity::getEquippedStack).map(ItemStack::getItem).anyMatch(heldItems.get()::contains));
    }

    @Override
    public NbtCompound toTag() {
        NbtCompound compound = new NbtCompound();
        compound.put("settings", this.settings.toTag());

        return compound;
    }

    @Override
    public EntitySelection fromTag(NbtCompound tag) {
        tag.getCompound("settings").ifPresent(this.settings::fromTag);

        return this;
    }

    public static class Screen extends WindowScreen {
        private final EntitySelection entitySelection;

        private WContainer settingsContainer;

        public Screen(GuiTheme theme, EntitySelection entitySelection, Runnable onChange) {
            super(theme, "Configure Entity Selection");
            this.entitySelection = entitySelection;
            this.onClosed(onChange);
        }

        @Override
        public void initWidgets() {
            this.entitySelection.settings.onActivated();

            if (!this.entitySelection.settings.groups.isEmpty()) {
                this.settingsContainer = add(theme.verticalList()).expandX().widget();
                this.settingsContainer.add(theme.settings(this.entitySelection.settings)).expandX();
            }
        }

        @Override
        public void tick() {
            super.tick();

            this.entitySelection.settings.tick(this.settingsContainer, theme);
        }
    }

    public enum Filter {
        Only,
        Exclude,
        Nothing;

        public boolean include(boolean property) {
            return switch (this) {
                case Only -> property;
                case Exclude -> !property;
                case Nothing -> true;
            };
        }


        @Override
        public String toString() {
            return this == Nothing ? "-" : super.toString();
        }
    }
}
