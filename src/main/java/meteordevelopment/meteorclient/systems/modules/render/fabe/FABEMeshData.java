/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.render.fabe;
import meteordevelopment.meteorclient.renderer.MeshBuilder;
import meteordevelopment.meteorclient.systems.modules.render.blockesp.ESPBlockData;
import net.minecraft.block.Block;
import net.minecraft.util.math.Box;

import java.util.List;

public record FABEMeshData(Box aabb,
                           List<TracerLine> tracerLines,
                           MeshBuilder lineBuilder,
                           MeshBuilder faceBuilder) {}
