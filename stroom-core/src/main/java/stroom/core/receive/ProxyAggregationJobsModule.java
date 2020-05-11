package stroom.core.receive;

import stroom.util.RunnableWrapper;
import stroom.job.api.ScheduledJobsBinder;

import com.google.inject.AbstractModule;

import javax.inject.Inject;

import static stroom.job.api.Schedule.ScheduleType.CRON;

public class ProxyAggregationJobsModule extends AbstractModule {
    @Override
    protected void configure() {
        super.configure();

        ScheduledJobsBinder.create(binder())
                .bindJobTo(ProxyAggregation.class, builder -> builder
                        .withName("Proxy Aggregation")
                        .withDescription("Job to pick up the data written by the proxy and store it in Stroom")
                        .withSchedule(CRON, "0,10,20,30,40,50 * *"));
    }

    private static class ProxyAggregation extends RunnableWrapper {
        @Inject
        ProxyAggregation(final ProxyAggregationExecutor proxyAggregationExecutor) {
            super(proxyAggregationExecutor::exec);
        }
    }
}
