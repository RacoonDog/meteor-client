/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.render.fabe;

import net.minecraft.block.Block;

public class FABEBlock {
    public final Block block;
    public final short x;
    public final short y;
    public final short z;
    public FABEGroup group = null;
    public boolean up = false;
    public boolean down = false;
    public boolean north = false;
    public boolean south = false;
    public boolean east = false;
    public boolean west = false;

    public FABEBlock(Block block, short x, short y, short z) {
        this.block = block;
        this.x = x;
        this.y = y;
        this.z = z;
    }
}
