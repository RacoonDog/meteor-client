/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.settings;

import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import meteordevelopment.meteorclient.utils.misc.ISerializable;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.InvalidIdentifierException;

import java.util.function.Predicate;

public class EntitySelection implements ISerializable<EntitySelection>, Predicate<Entity> {
    public final ReferenceOpenHashSet<EntityType<?>> entityTypes = new ReferenceOpenHashSet<>();

    @Override
    public boolean test(Entity entity) {
        return this.entityTypes.contains(entity.getType());
    }

    @Override
    public NbtCompound toTag() {
        NbtList data = new NbtList();

        for (EntityType<?> entityType : this.entityTypes) {
            data.add(NbtString.of(Registries.ENTITY_TYPE.getId(entityType).toString()));
        }

        NbtCompound compound = new NbtCompound();
        compound.put("data", data);

        return compound;
    }

    @Override
    public EntitySelection fromTag(NbtCompound tag) {
        this.entityTypes.clear();

        tag.getList("data").ifPresent(data -> {
            for (NbtElement element : data) {
                if (element instanceof NbtString(String value)) {
                    try {
                        this.entityTypes.add(Registries.ENTITY_TYPE.get(Identifier.of(value)));
                    } catch (InvalidIdentifierException ignored) {}
                }
            }
        });

        return this;
    }
}
