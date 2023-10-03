/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.utils.entity;

import com.google.common.collect.Multimap;
import meteordevelopment.meteorclient.mixin.ShulkerEntityAccessor;
import meteordevelopment.meteorclient.mixininterface.IAttributeContainer;
import meteordevelopment.meteorclient.mixininterface.IEntityAttributeInstance;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.*;
import net.minecraft.entity.mob.ShulkerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

public class EntityAttributeManager {
    /**
     * @see LivingEntity#getAttributes()
     */
    public static AttributeContainer getAttributes(LivingEntity entity) {
        @SuppressWarnings("unchecked")
        AttributeContainer attributes = new AttributeContainer(DefaultAttributeRegistry.get((EntityType<? extends LivingEntity>) entity.getType()));

        // Equipment
        for (var equipmentSlot : EquipmentSlot.values()) {
            ItemStack stack = entity.getEquippedStack(equipmentSlot);
            attributes.addTemporaryModifiers(stack.getAttributeModifiers(equipmentSlot));
        }

        // Status effects
        for (var statusEffect : StatusEffectManager.getStatusEffects(entity)) {
            statusEffect.getEffectType().onApplied(entity, attributes, statusEffect.getAmplifier());
        }

        handleSpecialCases(entity, attributes::getCustomInstance);

        // Copy tracked attributes
        ((IAttributeContainer) attributes).meteor$union(entity.getAttributes());

        return attributes;
    }

    /**
     * @see LivingEntity#getAttributeInstance(EntityAttribute)
     */
    public static EntityAttributeInstance getAttributeInstance(LivingEntity entity, EntityAttribute attribute) {
        @SuppressWarnings("unchecked")
        double baseValue = DefaultAttributeRegistry.get((EntityType<? extends LivingEntity>) entity.getType()).getBaseValue(attribute);
        EntityAttributeInstance attributeInstance = new EntityAttributeInstance(attribute, o1 -> {});
        attributeInstance.setBaseValue(baseValue);

        // Equipment
        for (var equipmentSlot : EquipmentSlot.values()) {
            ItemStack stack = entity.getEquippedStack(equipmentSlot);
            Multimap<EntityAttribute, EntityAttributeModifier> modifiers = stack.getAttributeModifiers(equipmentSlot);
            for (var modifier : modifiers.get(attribute)) attributeInstance.addTemporaryModifier(modifier);
        }

        // Status effects
        for (var statusEffect : StatusEffectManager.getStatusEffects(entity)) {
            EntityAttributeModifier modifier = statusEffect.getEffectType().getAttributeModifiers().get(attribute);
            if (modifier == null) continue;
            attributeInstance.addPersistentModifier(new EntityAttributeModifier(modifier.getId(), statusEffect.getTranslationKey() + " " + statusEffect.getAmplifier(), statusEffect.getEffectType().adjustModifierAmount(statusEffect.getAmplifier(), modifier), modifier.getOperation()));
        }

        handleSpecialCases(entity, someAttribute -> someAttribute == attribute ? attributeInstance : null);

        // Copy tracked modifiers
        EntityAttributeInstance trackedInstance = entity.getAttributeInstance(attribute);
        if (trackedInstance != null) ((IEntityAttributeInstance) attributeInstance).meteor$union(trackedInstance);

        return attributeInstance;
    }

    /**
     * @see LivingEntity#getAttributeValue(EntityAttribute)
     */
    public static double getAttributeValue(LivingEntity entity, EntityAttribute attribute) {
        return getAttributeInstance(entity, attribute).getValue();
    }

    private static void handleSpecialCases(LivingEntity entity, Function<EntityAttribute, EntityAttributeInstance> consumer) {
        if (entity instanceof ShulkerEntity shulkerEntity) {
            if (shulkerEntity.getDataTracker().get(ShulkerEntityAccessor.meteor$getPeekAmount()) == 0) {
                @Nullable EntityAttributeInstance attributeInstance = consumer.apply(EntityAttributes.GENERIC_ARMOR);
                if (attributeInstance != null) attributeInstance.addPersistentModifier(ShulkerEntityAccessor.meteor$getCoveredArmorBonus());
            }
        }
    }
}