package stroom.core.receive;

import stroom.job.api.ScheduledJobsModule;
import stroom.job.api.RunnableWrapper;

import javax.inject.Inject;

import static stroom.job.api.Schedule.ScheduleType.CRON;

public class ProxyAggregationJobsModule extends ScheduledJobsModule {
    @Override
    protected void configure() {
        super.configure();
        bindJob()
                .name("Proxy Aggregation")
                .description("Job to pick up the data written by the proxy and store it in Stroom")
                .schedule(CRON, "0,10,20,30,40,50 * *")
                .to(ProxyAggregation.class);
    }

    private static class ProxyAggregation extends RunnableWrapper {
        @Inject
        ProxyAggregation(final ProxyAggregationExecutor proxyAggregationExecutor) {
            super(proxyAggregationExecutor::exec);
        }
    }
}
