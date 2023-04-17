/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.utils.player;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixininterface.IVec3d;
import meteordevelopment.meteorclient.utils.PreInit;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.entity.fakeplayer.FakePlayerEntity;
import meteordevelopment.meteorclient.utils.world.ChunkCache;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.DamageUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.BlockView;
import net.minecraft.world.GameMode;
import net.minecraft.world.explosion.Explosion;

import java.util.Objects;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class DamageUtils {
    private static final Vec3d vec3d = new Vec3d(0, 0, 0);
    private static final DamageSource source = DamageSource.explosion(null);
    private static ChunkCache chunkCache;

    @PreInit
    public static void init() {
        MeteorClient.EVENT_BUS.subscribe(DamageUtils.class);
    }

    @EventHandler
    private static void onTick(TickEvent.Pre event) {
        chunkCache = null;
    }

    // Crystal damage

    public static double crystalDamage(PlayerEntity player, Vec3d crystal, boolean predictMovement, BlockPos obsidianPos, boolean ignoreTerrain) {
        if (player == null) return 0;
        if (EntityUtils.getGameMode(player) == GameMode.CREATIVE && !(player instanceof FakePlayerEntity)) return 0;

        ((IVec3d) vec3d).set(player.getPos().x, player.getPos().y, player.getPos().z);
        if (predictMovement) ((IVec3d) vec3d).set(vec3d.x + player.getVelocity().x, vec3d.y + player.getVelocity().y, vec3d.z + player.getVelocity().z);

        double modDistance = vec3d.squaredDistanceTo(crystal);
        if (modDistance > 144) return 0;

        if (chunkCache == null) chunkCache = ChunkCache.create();
        double exposure = getExposure(crystal, player, predictMovement, obsidianPos, ignoreTerrain);
        double impact = (1 - (Math.sqrt(modDistance) / 12)) * exposure;
        double damage = ((impact * impact + impact) / 2 * 7 * (6 * 2) + 1);

        damage = getDamageForDifficulty(damage);
        damage = DamageUtil.getDamageLeft((float) damage, (float) player.getArmor(), (float) player.getAttributeInstance(EntityAttributes.GENERIC_ARMOR_TOUGHNESS).getValue());
        damage = resistanceReduction(player, damage);

        damage = blastProtReduction(player, damage);

        return damage < 0 ? 0 : damage;
    }

    public static double crystalDamage(PlayerEntity player, Vec3d crystal) {
        return crystalDamage(player, crystal, false, null, false);
    }

    // Sword damage

    public static double getSwordDamage(PlayerEntity entity, boolean charged) {
        // Get sword damage
        double damage = 0;
        if (charged) {
            if (entity.getActiveItem().getItem() == Items.NETHERITE_SWORD) {
                damage += 8;
            } else if (entity.getActiveItem().getItem() == Items.DIAMOND_SWORD) {
                damage += 7;
            } else if (entity.getActiveItem().getItem() == Items.GOLDEN_SWORD) {
                damage += 4;
            } else if (entity.getActiveItem().getItem() == Items.IRON_SWORD) {
                damage += 6;
            } else if (entity.getActiveItem().getItem() == Items.STONE_SWORD) {
                damage += 5;
            } else if (entity.getActiveItem().getItem() == Items.WOODEN_SWORD) {
                damage += 4;
            }
            damage *= 1.5;
        }

        if (entity.getActiveItem().getEnchantments() != null) {
            if (EnchantmentHelper.get(entity.getActiveItem()).containsKey(Enchantments.SHARPNESS)) {
                int level = EnchantmentHelper.getLevel(Enchantments.SHARPNESS, entity.getActiveItem());
                damage += (0.5 * level) + 0.5;
            }
        }

        StatusEffectInstance strength = entity.getStatusEffect(StatusEffects.STRENGTH);
        if (strength != null) {
            damage += 3 * (strength.getAmplifier() + 1);
        }

        // Reduce by resistance
        damage = resistanceReduction(entity, damage);

        // Reduce by armour
        damage = DamageUtil.getDamageLeft((float) damage, (float) entity.getArmor(), (float) entity.getAttributeInstance(EntityAttributes.GENERIC_ARMOR_TOUGHNESS).getValue());

        // Reduce by enchants
        damage = normalProtReduction(entity, damage);

        return damage < 0 ? 0 : damage;
    }

    // Bed damage

    public static double bedDamage(LivingEntity player, Vec3d bed) {
        if (player instanceof PlayerEntity && ((PlayerEntity) player).getAbilities().creativeMode) return 0;

        double modDistance = Math.sqrt(player.squaredDistanceTo(bed));
        if (modDistance > 10) return 0;

        if (chunkCache == null) chunkCache = ChunkCache.create();
        double exposure = Explosion.getExposure(bed, player);
        double impact = (1.0 - (modDistance / 10.0)) * exposure;
        double damage = (impact * impact + impact) / 2 * 7 * (5 * 2) + 1;

        // Multiply damage by difficulty
        damage = getDamageForDifficulty(damage);

        // Reduce by resistance
        damage = resistanceReduction(player, damage);

        // Reduce by armour
        damage = DamageUtil.getDamageLeft((float) damage, (float) player.getArmor(), (float) player.getAttributeInstance(EntityAttributes.GENERIC_ARMOR_TOUGHNESS).getValue());

        // Reduce by enchants
        damage = blastProtReduction(player, damage);

        if (damage < 0) damage = 0;
        return damage;
    }

    // Anchor damage

    public static double anchorDamage(LivingEntity player, Vec3d anchor) {
        BlockPos anchorPos = new BlockPos(anchor);
        mc.world.removeBlock(anchorPos, false);
        double damage = bedDamage(player, anchor);
        mc.world.setBlockState(anchorPos, Blocks.RESPAWN_ANCHOR.getDefaultState());
        return damage;
    }

    // Utils

    private static double getDamageForDifficulty(double damage) {
        return switch (mc.world.getDifficulty()) {
            case PEACEFUL -> 0;
            case EASY     -> Math.min(damage / 2 + 1, damage);
            case HARD     -> damage * 3 / 2;
            default       -> damage;
        };
    }

    private static double normalProtReduction(Entity player, double damage) {
        if (damage < 0) return 0;

        int protLevel = EnchantmentHelper.getProtectionAmount(player.getArmorItems(), DamageSource.GENERIC);
        if (protLevel > 20) protLevel = 20;

        return damage * 1 - (protLevel / 25.0);
    }

    private static double blastProtReduction(Entity player, double damage) {
        if (damage < 0) return 0;

        int protLevel = EnchantmentHelper.getProtectionAmount(player.getArmorItems(), source);
        if (protLevel > 20) protLevel = 20;

        return damage * (1 - (protLevel / 25.0));
    }

    private static double resistanceReduction(LivingEntity player, double damage) {
        if (damage < 0) return 0;

        StatusEffectInstance resistance = player.getStatusEffect(StatusEffects.RESISTANCE);
        if (resistance != null) {
            damage *= 1 - (resistance.getAmplifier() + 1) * 0.2;
        }

        return damage;
    }

    private static double getExposure(Vec3d source, Entity entity, boolean predictMovement, BlockPos obsidianPos, boolean ignoreTerrain) {
        Box box = entity.getBoundingBox();
        if (predictMovement) {
            Vec3d v = entity.getVelocity();
            box = box.offset(v.x, v.y, v.z);
        }

        double d = 1 / ((box.maxX - box.minX) * 2 + 1);
        double e = 1 / ((box.maxY - box.minY) * 2 + 1);
        double f = 1 / ((box.maxZ - box.minZ) * 2 + 1);
        double g = (1 - Math.floor(1 / d) * d) / 2;
        double h = (1 - Math.floor(1 / f) * f) / 2;

        if (!(d < 0) && !(e < 0) && !(f < 0)) {
            int i = 0;
            int j = 0;

            for (double k = 0; k <= 1; k += d) {
                double n = MathHelper.lerp(k, box.minX, box.maxX);
                ((IVec3d) vec3d).setX(n + g);

                for (double l = 0; l <= 1; l += e) {
                    double o = MathHelper.lerp(l, box.minY, box.maxY);
                    ((IVec3d) vec3d).setY(o);

                    for (double m = 0; m <= 1; m += f) {
                        double p = MathHelper.lerp(m, box.minZ, box.maxZ);
                        ((IVec3d) vec3d).setZ(p + h);

                        if (raycast(vec3d, source, obsidianPos, ignoreTerrain) == HitResult.Type.MISS) i++;

                        j++;
                    }
                }
            }

            return (double) i / j;
        }

        return 0;
    }

    private static HitResult.Type raycast(Vec3d start, Vec3d end, BlockPos obsidianPos, boolean ignoreTerrain) {
        return BlockView.raycast(start, end, null, (_null, blockPos) -> {
            BlockState blockState;
            if (blockPos.equals(obsidianPos)) blockState = Blocks.OBSIDIAN.getDefaultState();
            else {
                blockState = chunkCache.get(blockPos.getX(), blockPos.getZ()).getBlockState(blockPos);
                if (blockState.getBlock().getBlastResistance() < 600 && ignoreTerrain) return null;
            }

            BlockHitResult hitResult = blockState.getOutlineShape(mc.world, blockPos).raycast(start, end, blockPos);
            return hitResult == null ? null : hitResult.getType();
        }, (_null) -> HitResult.Type.MISS);
    }
}
