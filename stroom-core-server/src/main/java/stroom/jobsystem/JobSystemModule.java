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
import com.google.inject.multibindings.Multibinder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import stroom.entity.CachingEntityManager;
import stroom.entity.StroomDatabaseInfo;
import stroom.entity.StroomEntityManager;
import stroom.entity.shared.Clearable;
import stroom.jobsystem.shared.JobManager;
import stroom.lifecycle.LifecycleServiceImpl;
import stroom.node.NodeCache;
import stroom.task.TaskHandler;
import stroom.task.TaskManager;
import stroom.task.cluster.ClusterDispatchAsyncHelper;
import stroom.util.spring.StroomBeanStore;
import stroom.util.spring.StroomScope;

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
    }


//    @Bean
//    @Scope(StroomScope.TASK)
//    public ClusterLockHandler clusterLockHandler(final ClusterDispatchAsyncHelper dispatchHelper) {
//        return new ClusterLockHandler(dispatchHelper);
//    }
//
//    @Bean
//    @Scope(value = StroomScope.TASK)
//    public DistributedTaskRequestClusterHandler distributedTaskRequestClusterHandler(final DistributedTaskFactoryBeanRegistry distributedTaskFactoryBeanRegistry) {
//        return new DistributedTaskRequestClusterHandler(distributedTaskFactoryBeanRegistry);
//    }
//
//    @Bean
//    @Scope(StroomScope.TASK)
//    public FetchJobDataHandler fetchJobDataHandler(final JobService jobService,
//                                                   final JobNodeService jobNodeService,
//                                                   final ClusterDispatchAsyncHelper dispatchHelper) {
//        return new FetchJobDataHandler(jobService, jobNodeService, dispatchHelper);
//    }
//
//    @Bean
//    @Scope(value = StroomScope.TASK)
//    public GetScheduledTimesHandler getScheduledTimesHandler(final ScheduleService scheduleService) {
//        return new GetScheduledTimesHandler(scheduleService);
//    }
//
//    @Bean
//    @Scope(value = StroomScope.TASK)
//    public JobNodeInfoClusterHandler jobNodeInfoClusterHandler(final JobNodeTrackerCache jobNodeTrackerCache) {
//        return new JobNodeInfoClusterHandler(jobNodeTrackerCache);
//    }
}