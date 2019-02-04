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

package stroom.job;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import stroom.entity.EntityTypeBinder;
import stroom.entity.FindService;
import stroom.job.api.DistributedTaskFetcher;
import stroom.job.api.JobNodeService;
import stroom.job.api.JobService;
import stroom.job.api.ScheduledJobsModule;
import stroom.job.api.ScheduledTaskExecutor;
import stroom.job.shared.FetchJobDataAction;
import stroom.job.shared.GetScheduledTimesAction;
import stroom.job.shared.Job;
import stroom.job.shared.JobManager;
import stroom.job.shared.JobNode;
import stroom.task.api.TaskHandlerBinder;

public class JobSystemModule extends AbstractModule {
    @Override
    protected void configure() {
        // Ensure the scheduled jobs binder is present even if we don't bind actual jobs.
        install(new ScheduledJobsModule());

        bind(JobNodeService.class).to(JobNodeServiceImpl.class);
        bind(JobService.class).to(JobServiceImpl.class);
        bind(ScheduleService.class).to(ScheduleServiceImpl.class);
        bind(ScheduledTaskExecutor.class).to(ScheduledTaskExecutorImpl.class);
        bind(JobManager.class).to(JobManagerImpl.class);
        bind(DistributedTaskFetcher.class).to(DistributedTaskFetcherImpl.class);

        TaskHandlerBinder.create(binder())
                .bind(DistributedTaskRequestClusterTask.class, DistributedTaskRequestClusterHandler.class)
                .bind(FetchJobDataAction.class, FetchJobDataHandler.class)
                .bind(GetScheduledTimesAction.class, GetScheduledTimesHandler.class)
                .bind(JobNodeInfoClusterTask.class, JobNodeInfoClusterHandler.class)
                .bind(ScheduledTask.class, ScheduledTaskHandler.class);

        EntityTypeBinder.create(binder())
                .bind(Job.ENTITY_TYPE, JobServiceImpl.class)
                .bind(JobNode.ENTITY_TYPE, JobNodeServiceImpl.class);

        final Multibinder<FindService> findServiceBinder = Multibinder.newSetBinder(binder(), FindService.class);
        findServiceBinder.addBinding().to(JobServiceImpl.class);
        findServiceBinder.addBinding().to(JobNodeServiceImpl.class);
    }
}