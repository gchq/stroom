package stroom.properties.global.impl.db;

import stroom.properties.global.api.GlobalProperty;
import stroom.properties.global.api.LoadGlobalPropertyAction;
import stroom.task.api.AbstractTaskHandler;
import stroom.task.api.TaskHandlerBean;

import javax.inject.Inject;

@TaskHandlerBean(task = LoadGlobalPropertyAction.class)
class LoadGlobalPropertyHandler extends AbstractTaskHandler<LoadGlobalPropertyAction, GlobalProperty> {
    private final GlobalPropertyService globalPropertyService;

    @Inject
    LoadGlobalPropertyHandler(final GlobalPropertyService globalPropertyService) {
        this.globalPropertyService = globalPropertyService;
    }

    @Override
    public GlobalProperty exec(final LoadGlobalPropertyAction action) {
        return globalPropertyService.load(action.getGlobalProperty());
    }
}
