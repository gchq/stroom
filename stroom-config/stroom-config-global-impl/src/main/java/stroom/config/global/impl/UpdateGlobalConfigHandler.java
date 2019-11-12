package stroom.config.global.impl;

import stroom.config.global.shared.ConfigProperty;
import stroom.config.global.shared.UpdateGlobalConfigAction;
import stroom.task.api.AbstractTaskHandler;

import javax.inject.Inject;


class UpdateGlobalConfigHandler extends AbstractTaskHandler<UpdateGlobalConfigAction, ConfigProperty> {
    private final GlobalConfigService globalConfigService;

    @Inject
    UpdateGlobalConfigHandler(final GlobalConfigService globalConfigService) {
        this.globalConfigService = globalConfigService;
    }

    @Override
    public ConfigProperty exec(final UpdateGlobalConfigAction action) {
        return globalConfigService.update(action.getConfigProperty());
    }
}
