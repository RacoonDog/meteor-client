/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.render.fabe;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.*;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.world.chunk.Chunk;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class FABEChunk {
    public final Chunk chunk;
    private final Long2ObjectMap<FABEBlock> blocks = new Long2ObjectOpenHashMap<>();
    private final Reference2ObjectMap<Block, List<FABEGroup>> groups = new Reference2ObjectOpenHashMap<>();
    private @Nullable Chunk westChunk;
    private @Nullable Chunk eastChunk;
    private @Nullable Chunk northChunk;
    private @Nullable Chunk southChunk;

    public FABEChunk(Chunk chunk) {
        this.chunk = chunk;
    }

    private Chunk westChunk() {
        if (westChunk == null) {
            westChunk = MinecraftClient.getInstance().world.getChunk(chunk.getPos().x - 1, chunk.getPos().z);
        }
        return westChunk;
    }

    private Chunk eastChunk() {
        if (eastChunk == null) {
            eastChunk = MinecraftClient.getInstance().world.getChunk(chunk.getPos().x + 1, chunk.getPos().z);
        }
        return eastChunk;
    }

    private Chunk northChunk() {
        if (northChunk == null) {
            northChunk = MinecraftClient.getInstance().world.getChunk(chunk.getPos().x, chunk.getPos().z - 1);
        }
        return northChunk;
    }

    private Chunk southChunk() {
        if (southChunk == null) {
            southChunk = MinecraftClient.getInstance().world.getChunk(chunk.getPos().x, chunk.getPos().z + 1);
        }
        return southChunk;
    }

    private BlockState getBlockState(Chunk chunk, short x, short y, short z) {
        return chunk.getSection(chunk.getSectionIndex(y)).getBlockState(x, y & 15, z);
    }

    public void add(BlockState state, short x, short y, short z) {
        Block block = state.getBlock();
        FABEBlock sblock = new FABEBlock(block, x, y, z);

        blocks.put(StoragePointer.key(x, y, z), sblock);

        // grouping & culling
        @Nullable FABEBlock other;
        if (x != 0) {
            if ((other = blocks.get(StoragePointer.key(x - 1, y, z))) != null && other.block == block) {
                sblock.west = true;
                other.east = true;

                other.group.add(sblock, x, y, z);
            }

            if (x == 15 && eastChunk() != null && getBlockState(eastChunk(), (short) 0, y, z).getBlock() == block) {
                sblock.east = true;
            }
        } else if (westChunk() != null && getBlockState(westChunk(), (short) 15, y, z).getBlock() == block) {
            sblock.west = true;
        }

        if (y != this.chunk.getBottomY() && (other = blocks.get(StoragePointer.key(x, y - 1, z))) != null && other.block == block) {
            sblock.down = true;
            other.up = true;

            if (sblock.group == null) {
                other.group.add(sblock, x, y, z);
            } else if (sblock.group != other.group) {
                other.group.union(sblock.group, this);
            }
        }

        if (z != 0) {
            if ((other = blocks.get(StoragePointer.key(x, y, z - 1))) != null && other.block == block) {
                sblock.north = true;
                other.south = true;

                if (sblock.group == null) {
                    other.group.add(sblock, x, y, z);
                } else if (sblock.group != other.group) {
                    other.group.union(sblock.group, this);
                }
            }

            if (z == 15 && southChunk() != null && getBlockState(southChunk(), x, y, (short) 0).getBlock() == block) {
                sblock.south = true;
            }
        }  else if (northChunk() != null && getBlockState(northChunk(), x, y, (short) 15).getBlock() == block) {
            sblock.north = true;
        }

        if (sblock.group == null) {
            FABEGroup group = new FABEGroup(block);
            group.add(sblock, x, y, z);
            add(group);
        }
    }

    public void add(FABEGroup group) {
        groups.computeIfAbsent(group.block, k -> new ObjectArrayList<>()).add(group);
    }

    public void remove(FABEGroup group) {
        groups.getOrDefault(group.block, List.of()).remove(group);
    }

    public boolean isEmpty() {
        return this.groups.isEmpty();
    }

    public List<FABEMeshData> mesh() {
        ObjectArrayList<FABEMeshData> meshes = new ObjectArrayList<>();

        for (var entry : Reference2ObjectMaps.fastIterable(groups)) {
            meshes.add(FABEGroup.mesh(entry.getKey(), chunk.getPos(), entry.getValue()));
        }

        return meshes;
    }
}
