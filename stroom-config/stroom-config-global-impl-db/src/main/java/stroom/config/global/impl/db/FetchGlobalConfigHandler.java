package stroom.config.global.impl.db;

import stroom.config.global.api.FetchGlobalConfigAction;
import stroom.config.global.api.ConfigProperty;
import stroom.task.api.AbstractTaskHandler;
import stroom.task.api.TaskHandlerBean;
import stroom.util.shared.SharedList;

import javax.inject.Inject;

@TaskHandlerBean(task = FetchGlobalConfigAction.class)
class FetchGlobalConfigHandler extends AbstractTaskHandler<FetchGlobalConfigAction, SharedList<ConfigProperty>> {
    private final GlobalConfigService globalConfigService;

    @Inject
    FetchGlobalConfigHandler(final GlobalConfigService globalConfigService) {
        this.globalConfigService = globalConfigService;
    }

    @Override
    public SharedList<ConfigProperty> exec(final FetchGlobalConfigAction task) {
        return new SharedList<>(globalConfigService.list());
    }
}
