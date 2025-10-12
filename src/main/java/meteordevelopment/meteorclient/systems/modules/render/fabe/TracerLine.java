/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.render.fabe;

import meteordevelopment.meteorclient.systems.modules.render.blockesp.ESPBlockData;

public record TracerLine(ESPBlockData blockData, double x, double y, double z) {}
