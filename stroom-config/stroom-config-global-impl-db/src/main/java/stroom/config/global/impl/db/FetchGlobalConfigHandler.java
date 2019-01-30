package stroom.config.global.impl.db;

import stroom.config.global.api.ConfigProperty;
import stroom.config.global.api.FetchGlobalConfigAction;
import stroom.task.api.AbstractTaskHandler;

import javax.inject.Inject;


class FetchGlobalConfigHandler extends AbstractTaskHandler<FetchGlobalConfigAction, ConfigProperty> {
    private final GlobalConfigService globalConfigService;

    @Inject
    FetchGlobalConfigHandler(final GlobalConfigService globalConfigService) {
        this.globalConfigService = globalConfigService;
    }

    @Override
    public ConfigProperty exec(final FetchGlobalConfigAction action) {
        return globalConfigService.fetch(action.getConfigId());
    }
}
