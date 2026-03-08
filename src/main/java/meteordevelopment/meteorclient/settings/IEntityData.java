/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.settings;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WidgetScreen;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.utils.misc.IChangeable;
import meteordevelopment.meteorclient.utils.misc.ICopyable;
import meteordevelopment.meteorclient.utils.misc.ISerializable;
import net.minecraft.block.Block;
import org.jetbrains.annotations.Nullable;

public interface IEntityData<T extends ICopyable<T> & ISerializable<T> & IEntityData<T>> {
    WidgetScreen createScreen(GuiTheme theme, EntitySelection selection, EntitySelectionDataSetting<T> setting);
    void addWidgets(GuiTheme theme, WTable table, EntitySelection selection, EntitySelectionDataSetting<T> setting);
    boolean isValid();
}
