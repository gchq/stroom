package stroom.activity.impl;

import com.google.inject.AbstractModule;
import stroom.activity.api.ActivityService;
import stroom.activity.api.CurrentActivity;
import stroom.activity.shared.CreateActivityAction;
import stroom.activity.shared.DeleteActivityAction;
import stroom.activity.shared.FetchActivityAction;
import stroom.activity.shared.FindActivityAction;
import stroom.activity.shared.SetCurrentActivityAction;
import stroom.activity.shared.UpdateActivityAction;
import stroom.dashboard.shared.ValidateExpressionAction;
import stroom.task.api.TaskHandlerBinder;

public class ActivityModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(ActivityService.class).to(ActivityServiceImpl.class);
        bind(CurrentActivity.class).to(CurrentActivityImpl.class);

        TaskHandlerBinder.create(binder())
                .bind(CreateActivityAction.class, CreateActivityHandler.class)
                .bind(UpdateActivityAction.class, UpdateActivityHandler.class)
                .bind(DeleteActivityAction.class, DeleteActivityHandler.class)
                .bind(FetchActivityAction.class, FetchActivityHandler.class)
                .bind(FindActivityAction.class, FindActivityHandler.class)
                .bind(SetCurrentActivityAction.class, SetCurrentActivityHandler.class)
                .bind(ValidateExpressionAction.class, ValidateActivityHandler.class);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return 0;
    }
}
