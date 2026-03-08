/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.render.esp;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.widgets.containers.WContainer;
import meteordevelopment.meteorclient.settings.EntitySelection;
import meteordevelopment.meteorclient.settings.EntitySelectionDataSetting;
import meteordevelopment.meteorclient.settings.GenericSetting;

public class ESPEntityDataScreen extends WindowScreen {
    private final ESPEntityData entityData;

    private WContainer settingsContainer;

    public ESPEntityDataScreen(GuiTheme theme, ESPEntityData entityData, EntitySelection selection, EntitySelectionDataSetting<ESPEntityData> setting) {
        this(theme, entityData);
        this.onClosed(setting::onChanged);
    }

    public ESPEntityDataScreen(GuiTheme theme, ESPEntityData entityData, GenericSetting<ESPEntityData> setting) {
        this(theme, entityData);
        this.onClosed(setting::onChanged);
    }

    private ESPEntityDataScreen(GuiTheme theme, ESPEntityData entityData) {
        super(theme, "Configure Entity Selection");

        this.entityData = entityData;
    }

    @Override
    public void initWidgets() {
        this.entityData.settings.onActivated();

        if (!this.entityData.settings.groups.isEmpty()) {
            this.settingsContainer = add(theme.verticalList()).expandX().widget();
            this.settingsContainer.add(theme.settings(this.entityData.settings)).expandX();
        }
    }

    @Override
    public void tick() {
        super.tick();

        this.entityData.settings.tick(this.settingsContainer, theme);
    }
}
