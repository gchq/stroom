package stroom.storedquery.impl;

import stroom.job.api.ScheduledJobsBinder;
import stroom.storedquery.api.StoredQueryService;
import stroom.util.RunnableWrapper;
import stroom.util.guice.RestResourcesBinder;
import stroom.util.shared.scheduler.CronExpressions;

import com.google.inject.AbstractModule;
import jakarta.inject.Inject;

public class StoredQueryModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(StoredQueryService.class).to(StoredQueryServiceImpl.class);

        RestResourcesBinder.create(binder())
                .bind(StoredQueryResourceImpl.class);

        ScheduledJobsBinder.create(binder())
                .bindJobTo(QueryHistoryClean.class, builder -> builder
                        .name("Query History Clean")
                        .description("Job to clean up old query history items")
                        .cronSchedule(CronExpressions.EVERY_DAY_AT_MIDNIGHT.getExpression())
                        .advanced(false));
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        return o != null && getClass() == o.getClass();
    }

    @Override
    public int hashCode() {
        return 0;
    }

    private static class QueryHistoryClean extends RunnableWrapper {

        @Inject
        QueryHistoryClean(final StoredQueryHistoryCleanExecutor queryHistoryCleanExecutor) {
            super(queryHistoryCleanExecutor::exec);
        }
    }
}
