package stroom.properties.global.impl.db;

import stroom.properties.global.api.FetchGlobalPropertiesAction;
import stroom.properties.global.api.GlobalProperty;
import stroom.task.api.AbstractTaskHandler;
import stroom.task.api.TaskHandlerBean;
import stroom.util.shared.SharedList;

import javax.inject.Inject;

@TaskHandlerBean(task = FetchGlobalPropertiesAction.class)
class FetchGlobalPropertiesHandler extends AbstractTaskHandler<FetchGlobalPropertiesAction, SharedList<GlobalProperty>> {
    private final GlobalPropertyService globalPropertyService;

    @Inject
    FetchGlobalPropertiesHandler(final GlobalPropertyService globalPropertyService) {
        this.globalPropertyService = globalPropertyService;
    }

    @Override
    public SharedList<GlobalProperty> exec(final FetchGlobalPropertiesAction task) {
        return new SharedList<>(globalPropertyService.list());
    }
}
