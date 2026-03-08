/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.render.esp;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.settings.EntitySelection;
import meteordevelopment.meteorclient.settings.EntitySelectionDataSetting;
import meteordevelopment.meteorclient.settings.GenericSetting;
import meteordevelopment.meteorclient.settings.Setting;

public class ESPEntityDataScreen extends WindowScreen {
    private final ESPEntityData entityData;
    private final Setting<?> setting;

    public ESPEntityDataScreen(GuiTheme theme, ESPEntityData entityData, EntitySelection selection, EntitySelectionDataSetting<ESPEntityData> setting) {
        this(theme, entityData, setting, null);
    }

    public ESPEntityDataScreen(GuiTheme theme, ESPEntityData entityData, GenericSetting<ESPEntityData> setting) {
        this(theme, entityData, setting, null);
    }

    private ESPEntityDataScreen(GuiTheme theme, ESPEntityData entityData, Setting<?> setting, Void nothing) {
        super(theme, "Configure Entity Selection");

        this.entityData = entityData;
        this.setting = setting;
    }

    @Override
    public void initWidgets() {
        this.entityData.settings.onActivated();
        add(theme.settings(this.entityData.settings)).expandX();
    }
}
