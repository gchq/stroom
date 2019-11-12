package stroom.config.global.impl;

import stroom.config.global.shared.ConfigProperty;
import stroom.config.global.shared.FetchGlobalConfigAction;
import stroom.task.api.AbstractTaskHandler;
import stroom.util.logging.LogUtil;

import javax.inject.Inject;


class FetchGlobalConfigHandler extends AbstractTaskHandler<FetchGlobalConfigAction, ConfigProperty> {
    private final GlobalConfigService globalConfigService;

    @Inject
    FetchGlobalConfigHandler(final GlobalConfigService globalConfigService) {
        this.globalConfigService = globalConfigService;
    }

    @Override
    public ConfigProperty exec(final FetchGlobalConfigAction action) {
        return globalConfigService.fetch(action.getConfigId()).orElseThrow(() ->
                new RuntimeException(LogUtil.message("No config property found with ID [{}]", action.getConfigId())));
    }
}
