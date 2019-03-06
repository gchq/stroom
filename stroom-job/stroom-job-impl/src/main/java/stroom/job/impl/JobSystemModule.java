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

import com.google.inject.AbstractModule;
import stroom.event.logging.api.ObjectInfoProviderBinder;
import stroom.job.api.JobManager;
import stroom.job.api.ScheduledJobsModule;
import stroom.job.shared.FindJobAction;
import stroom.job.shared.FindJobNodeAction;
import stroom.job.shared.GetScheduledTimesAction;
import stroom.job.shared.Job;
import stroom.job.shared.JobNode;
import stroom.job.shared.UpdateJobAction;
import stroom.job.shared.UpdateJobNodeAction;
import stroom.task.api.TaskHandlerBinder;

public class JobSystemModule extends AbstractModule {
    @Override
    protected void configure() {
        // Ensure the scheduled jobs binder is present even if we don't bind actual jobs.
        install(new ScheduledJobsModule());
//
//        bind(JobNodeService.class).to(JobNodeService.class);
//        bind(JobService.class).to(JobService.class);
//        bind(ScheduleService.class).to(ScheduleService.class);
//        bind(ScheduledTaskExecutor.class).to(ScheduledTaskExecutor.class);
        bind(JobManager.class).to(JobManagerImpl.class);
//        bind(DistributedTaskFetcher.class).to(DistributedTaskFetcher.class);

        TaskHandlerBinder.create(binder())
                .bind(DistributedTaskRequestClusterTask.class, DistributedTaskRequestClusterHandler.class)
                .bind(FindJobAction.class, FindJobHandler.class)
                .bind(FindJobNodeAction.class, FindJobNodeHandler.class)
                .bind(GetScheduledTimesAction.class, GetScheduledTimesHandler.class)
                .bind(JobNodeInfoClusterTask.class, JobNodeInfoClusterHandler.class)
                .bind(ScheduledTask.class, ScheduledTaskHandler.class)
                .bind(UpdateJobAction.class, UpdateJobHandler.class)
                .bind(UpdateJobNodeAction.class, UpdateJobNodeHandler.class);

        // Provide object info to the logging service.
        ObjectInfoProviderBinder.create(binder())
                .bind(Job.class, JobObjectInfoProvider.class)
                .bind(JobNode.class, JobNodeObjectInfoProvider.class);

//        EntityTypeBinder.create(binder())
//                .bind(Job.ENTITY_TYPE, JobServiceImpl.class)
//                .bind(JobNode.ENTITY_TYPE, JobNodeServiceImpl.class);
//
//        final Multibinder<FindService> findServiceBinder = Multibinder.newSetBinder(binder(), FindService.class);
//        findServiceBinder.addBinding().to(JobServiceImpl.class);
//        findServiceBinder.addBinding().to(JobNodeServiceImpl.class);
    }
}