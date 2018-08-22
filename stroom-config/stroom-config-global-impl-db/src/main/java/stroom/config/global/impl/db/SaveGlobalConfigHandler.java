package stroom.config.global.impl.db;

import stroom.properties.global.api.ConfigProperty;
import stroom.properties.global.api.SaveGlobalConfigAction;
import stroom.task.api.AbstractTaskHandler;
import stroom.task.api.TaskHandlerBean;

import javax.inject.Inject;

@TaskHandlerBean(task = SaveGlobalConfigAction.class)
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
