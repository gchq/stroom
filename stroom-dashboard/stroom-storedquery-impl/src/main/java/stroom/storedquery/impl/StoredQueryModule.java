package stroom.storedquery.impl;

import com.google.inject.AbstractModule;
import stroom.dashboard.shared.CreateStoredQueryAction;
import stroom.dashboard.shared.DeleteStoredQueryAction;
import stroom.dashboard.shared.FetchStoredQueryAction;
import stroom.dashboard.shared.FindStoredQueryAction;
import stroom.dashboard.shared.UpdateStoredQueryAction;
import stroom.storedquery.api.StoredQueryService;
import stroom.task.api.TaskHandlerBinder;

public class StoredQueryModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(StoredQueryService.class).to(StoredQueryServiceImpl.class);

        TaskHandlerBinder.create(binder())
                .bind(CreateStoredQueryAction.class, CreateStoredQueryHandler.class)
                .bind(UpdateStoredQueryAction.class, UpdateStoredQueryHandler.class)
                .bind(DeleteStoredQueryAction.class, DeleteStoredQueryHandler.class)
                .bind(FetchStoredQueryAction.class, FetchStoredQueryHandler.class)
                .bind(FindStoredQueryAction.class, FindStoredQueryHandler.class);
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
