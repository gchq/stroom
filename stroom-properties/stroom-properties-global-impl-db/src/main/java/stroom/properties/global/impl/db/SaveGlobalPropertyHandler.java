package stroom.properties.global.impl.db;

import stroom.properties.global.api.GlobalProperty;
import stroom.properties.global.api.SaveGlobalPropertyAction;
import stroom.task.api.AbstractTaskHandler;
import stroom.task.api.TaskHandlerBean;

import javax.inject.Inject;

@TaskHandlerBean(task = SaveGlobalPropertyAction.class)
class SaveGlobalPropertyHandler extends AbstractTaskHandler<SaveGlobalPropertyAction, GlobalProperty> {
    private final GlobalPropertyService globalPropertyService;

    @Inject
    SaveGlobalPropertyHandler(final GlobalPropertyService globalPropertyService) {
        this.globalPropertyService = globalPropertyService;
    }

    @Override
    public GlobalProperty exec(final SaveGlobalPropertyAction action) {
        return globalPropertyService.save(action.getGlobalProperty());
    }
}
