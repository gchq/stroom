/*
 * Copyright 2018 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.job.impl;

import stroom.event.logging.api.ObjectInfoProviderBinder;
import stroom.job.api.JobManager;
import stroom.job.api.ScheduledJobsBinder;
import stroom.job.shared.Job;
import stroom.job.shared.JobNode;
import stroom.lifecycle.api.LifecycleBinder;
import stroom.util.RunnableWrapper;
import stroom.util.guice.RestResourcesBinder;

import com.google.inject.AbstractModule;

import javax.inject.Inject;

import static stroom.job.api.Schedule.ScheduleType.PERIODIC;

public class JobSystemModule extends AbstractModule {
    @Override
    protected void configure() {

        bind(JobManager.class).to(JobManagerImpl.class);

        RestResourcesBinder.create(binder())
                .bind(JobResourceImpl.class)
                .bind(JobNodeResourceImpl.class)
                .bind(ScheduledTimeResourceImpl.class);

        // Provide object info to the logging service.
        ObjectInfoProviderBinder.create(binder())
                .bind(Job.class, JobObjectInfoProvider.class)
                .bind(JobNode.class, JobNodeObjectInfoProvider.class);


        // Ensure the scheduled jobs binder is present even if we don't bind actual jobs.
        ScheduledJobsBinder.create(binder())
                .bindJobTo(FetchNewTasks.class, builder -> builder
                        .withName("Fetch new tasks")
                        .withDescription("Every 10 seconds the Stroom lifecycle service will try and " +
                                "fetch new tasks for execution.")
                        .withManagedState(false)
                        .withSchedule(PERIODIC, "10s"));

        // Make sure the last thing to start and the first thing to stop is the scheduled task executor.
        LifecycleBinder.create(binder())
                .bindStartupTaskTo(JobBootstrapStartup.class)
                .bindShutdownTaskTo(DistributedTaskFetcherShutdown.class, 999)
                .bindStartupTaskTo(ScheduledTaskExecutorStartup.class, Integer.MIN_VALUE)
                .bindShutdownTaskTo(ScheduledTaskExecutorShutdown.class, Integer.MIN_VALUE);
    }

    private static class FetchNewTasks extends RunnableWrapper {
        @Inject
        FetchNewTasks(final DistributedTaskFetcher distributedTaskFetcher) {
            super(distributedTaskFetcher::execute);
        }
    }

    private static class JobBootstrapStartup extends RunnableWrapper {
        @Inject
        JobBootstrapStartup(final JobBootstrap jobBootstrap) {
            super(jobBootstrap::startup);
        }
    }

    private static class DistributedTaskFetcherShutdown extends RunnableWrapper {
        @Inject
        DistributedTaskFetcherShutdown(final DistributedTaskFetcher distributedTaskFetcher) {
            super(distributedTaskFetcher::shutdown);
        }
    }

    private static class ScheduledTaskExecutorStartup extends RunnableWrapper {
        @Inject
        ScheduledTaskExecutorStartup(final ScheduledTaskExecutor scheduledTaskExecutor) {
            super(scheduledTaskExecutor::startup);
        }
    }

    private static class ScheduledTaskExecutorShutdown extends RunnableWrapper {
        @Inject
        ScheduledTaskExecutorShutdown(final ScheduledTaskExecutor scheduledTaskExecutor) {
            super(scheduledTaskExecutor::shutdown);
        }
    }
}