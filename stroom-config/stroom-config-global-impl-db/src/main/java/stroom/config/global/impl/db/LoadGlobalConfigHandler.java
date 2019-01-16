package stroom.config.global.impl.db;

import stroom.config.global.api.ConfigProperty;
import stroom.config.global.api.LoadGlobalConfigAction;
import stroom.task.api.AbstractTaskHandler;

import javax.inject.Inject;


class LoadGlobalConfigHandler extends AbstractTaskHandler<LoadGlobalConfigAction, ConfigProperty> {
    private final GlobalConfigService globalConfigService;

    @Inject
    LoadGlobalConfigHandler(final GlobalConfigService globalConfigService) {
        this.globalConfigService = globalConfigService;
    }

    @Override
    public ConfigProperty exec(final LoadGlobalConfigAction action) {
        return globalConfigService.load(action.getConfigProperty());
    }
}
