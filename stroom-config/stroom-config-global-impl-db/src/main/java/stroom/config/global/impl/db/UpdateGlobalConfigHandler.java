package stroom.config.global.impl.db;

import stroom.config.global.api.ConfigProperty;
import stroom.config.global.api.UpdateGlobalConfigAction;
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
