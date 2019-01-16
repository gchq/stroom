package stroom.config.global.impl.db;

import stroom.config.global.api.ConfigProperty;
import stroom.config.global.api.SaveGlobalConfigAction;
import stroom.task.api.AbstractTaskHandler;

import javax.inject.Inject;


class SaveGlobalConfigHandler extends AbstractTaskHandler<SaveGlobalConfigAction, ConfigProperty> {
    private final GlobalConfigService globalConfigService;

    @Inject
    SaveGlobalConfigHandler(final GlobalConfigService globalConfigService) {
        this.globalConfigService = globalConfigService;
    }

    @Override
    public ConfigProperty exec(final SaveGlobalConfigAction action) {
        return globalConfigService.save(action.getConfigProperty());
    }
}
