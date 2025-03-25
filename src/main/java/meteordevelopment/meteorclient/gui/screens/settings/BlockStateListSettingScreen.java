/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.gui.screens.settings;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.mixin.IdentifierAccessor;
import meteordevelopment.meteorclient.settings.BlockStateListSetting;
import net.minecraft.block.WallBannerBlock;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

public class BlockStateListSettingScreen extends RegistryListSettingScreen<BlockStateListSetting.StateEntry> {
    private static final Identifier ID = Identifier.of("minecraft", "");

    public BlockStateListSettingScreen(GuiTheme theme, BlockStateListSetting setting) {
        super(theme, "Select Blocks", setting, setting.get(), setting.universe);
    }

    @Override
    protected WWidget getValueWidget(BlockStateListSetting.StateEntry value) {
        return theme.itemWithLabel(value.block().asItem().getDefaultStack(), getValueName(value));
    }

    @Override
    protected String getValueName(BlockStateListSetting.StateEntry value) {
        return value.toString();
    }

    @Override
    protected boolean skipValue(BlockStateListSetting.StateEntry value) {
        return value.block() instanceof WallBannerBlock;
    }

    @Override
    protected BlockStateListSetting.StateEntry getAdditionalValue(BlockStateListSetting.StateEntry value) {
        String path = Registries.BLOCK.getId(value.block()).getPath();
        if (!path.endsWith("_banner")) return null;

        ((IdentifierAccessor) (Object) ID).setPath(path.substring(0, path.length() - 6) + "wall_banner");
        return BlockStateListSetting.StateEntry.allOf(Registries.BLOCK.get(ID));
    }
}
