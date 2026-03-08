/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.gui.widgets;

import com.mojang.blaze3d.textures.FilterMode;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceArrayMap;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.renderer.Texture;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EntityType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class WEntity extends WWidget {
    private static final Map<EntityType<?>, Item> SPAWN_EGG_ITEMS = new Reference2ReferenceArrayMap<>();
    private static @Nullable Texture EMPTY_SPAWN_EGG_TEXTURE;

    protected @Nullable ItemStack itemStack;

    public WEntity(EntityType<?> entityType) {
        this.updateStack(entityType);
    }

    protected void updateStack(EntityType<?> entityType) {
        if (SPAWN_EGG_ITEMS.isEmpty()) {
            Registries.ITEM.stream()
                .filter(item -> item.getComponents().contains(DataComponentTypes.ENTITY_DATA))
                .forEach(item -> SPAWN_EGG_ITEMS.put(item.getComponents().get(DataComponentTypes.ENTITY_DATA).getType(), item));
        }

        @Nullable Item spawnEggItem = SPAWN_EGG_ITEMS.get(entityType);
        if (spawnEggItem != null) {
            this.itemStack = spawnEggItem.getDefaultStack();
        } else {
            this.itemStack = null;
        }
    }

    @Override
    protected void onCalculateSize() {
        double s = theme.scale(32);

        width = s;
        height = s;
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        if (this.itemStack != null) {
            renderer.post(() -> {
                double s = theme.scale(2);
                renderer.item(itemStack, (int) x, (int) y, (float) s, true);
            });
        } else {
            if (EMPTY_SPAWN_EGG_TEXTURE == null) {
                EMPTY_SPAWN_EGG_TEXTURE = Texture.readResource("/assets/meteor-client/textures/empty_spawn_egg.png", false, FilterMode.NEAREST);
            }

            renderer.texture(x, y, 32, 32, 0, EMPTY_SPAWN_EGG_TEXTURE);
        }
    }

    public void set(EntityType<?> entityType) {
        this.updateStack(entityType);
    }
}
