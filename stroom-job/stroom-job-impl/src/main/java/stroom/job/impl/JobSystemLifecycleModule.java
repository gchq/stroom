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

import stroom.lifecycle.api.LifecycleBinder;
import stroom.util.RunnableWrapper;

import com.google.inject.AbstractModule;

import javax.inject.Inject;

public class JobSystemLifecycleModule extends AbstractModule {

    @Override
    protected void configure() {

        // Make sure the last thing to start and the first thing to stop is the scheduled task executor.
        LifecycleBinder.create(binder())
                .bindStartupTaskTo(JobBootstrapStartup.class)
                .bindShutdownTaskTo(DistributedTaskFetcherShutdown.class, 999)
                .bindStartupTaskTo(ScheduledTaskExecutorStartup.class, Integer.MIN_VALUE)
                .bindShutdownTaskTo(ScheduledTaskExecutorShutdown.class, Integer.MIN_VALUE);
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