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

package stroom.jobsystem;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.multibindings.Multibinder;
import stroom.entity.FindService;
import stroom.entity.shared.Clearable;
import stroom.jobsystem.shared.Job;
import stroom.jobsystem.shared.JobManager;
import stroom.jobsystem.shared.JobNode;
import stroom.task.TaskHandler;

public class JobSystemModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(ClusterLockService.class).to(ClusterLockServiceImpl.class);
        bind(ClusterLockServiceTransactionHelper.class).to(ClusterLockServiceTransactionHelperImpl.class);
        bind(JobNodeService.class).to(JobNodeServiceImpl.class);
        bind(JobService.class).to(JobServiceImpl.class);
        bind(ScheduleService.class).to(ScheduleServiceImpl.class);
        bind(ScheduledTaskExecutor.class).to(ScheduledTaskExecutorImpl.class);
        bind(JobManager.class).to(JobManagerImpl.class);

        final Multibinder<Clearable> clearableBinder = Multibinder.newSetBinder(binder(), Clearable.class);
        clearableBinder.addBinding().to(ClusterLockServiceTransactionHelperImpl.class);

        final Multibinder<TaskHandler> taskHandlerBinder = Multibinder.newSetBinder(binder(), TaskHandler.class);
        taskHandlerBinder.addBinding().to(stroom.jobsystem.ClusterLockClusterHandler.class);
        taskHandlerBinder.addBinding().to(stroom.jobsystem.ClusterLockHandler.class);
        taskHandlerBinder.addBinding().to(stroom.jobsystem.DistributedTaskRequestClusterHandler.class);
        taskHandlerBinder.addBinding().to(stroom.jobsystem.FetchJobDataHandler.class);
        taskHandlerBinder.addBinding().to(stroom.jobsystem.GetScheduledTimesHandler.class);
        taskHandlerBinder.addBinding().to(stroom.jobsystem.JobNodeInfoClusterHandler.class);

        final MapBinder<String, Object> entityServiceByTypeBinder = MapBinder.newMapBinder(binder(), String.class, Object.class);
        entityServiceByTypeBinder.addBinding(Job.ENTITY_TYPE).to(JobServiceImpl.class);
        entityServiceByTypeBinder.addBinding(JobNode.ENTITY_TYPE).to(JobNodeServiceImpl.class);

        final Multibinder<FindService> findServiceBinder = Multibinder.newSetBinder(binder(), FindService.class);
        findServiceBinder.addBinding().to(JobServiceImpl.class);
        findServiceBinder.addBinding().to(JobNodeServiceImpl.class);
    }
}