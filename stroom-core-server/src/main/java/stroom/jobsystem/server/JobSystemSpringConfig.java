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

package stroom.jobsystem.server;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import stroom.entity.server.util.StroomDatabaseInfo;
import stroom.entity.server.util.StroomEntityManager;
import stroom.jobsystem.shared.JobManager;
import stroom.lifecycle.LifecycleServiceImpl;
import stroom.node.server.NodeCache;
import stroom.task.cluster.ClusterDispatchAsyncHelper;
import stroom.task.server.TaskManager;
import stroom.util.spring.StroomBeanStore;
import stroom.util.spring.StroomScope;

@Configuration
public class JobSystemSpringConfig {
    @Bean
    @Scope(value = StroomScope.SINGLETON)
    public ClusterLockClusterHandler clusterLockClusterHandler() {
        return new ClusterLockClusterHandler();
    }

    @Bean
    @Scope(StroomScope.TASK)
    public ClusterLockHandler clusterLockHandler(final ClusterDispatchAsyncHelper dispatchHelper) {
        return new ClusterLockHandler(dispatchHelper);
    }

    @Bean
    public ClusterLockService clusterLockService(final StroomEntityManager entityManager,
                                                 final StroomDatabaseInfo stroomDatabaseInfo,
                                                 final ClusterLockServiceTransactionHelper clusterLockServiceTransactionHelper,
                                                 final TaskManager taskManager,
                                                 final NodeCache nodeCache) {
        return new ClusterLockServiceImpl(entityManager, stroomDatabaseInfo, clusterLockServiceTransactionHelper, taskManager, nodeCache);
    }

    @Bean
    public ClusterLockServiceTransactionHelper clusterLockServiceTransactionHelper(final StroomEntityManager entityManager) {
        return new ClusterLockServiceTransactionHelperImpl(entityManager);
    }

    @Bean
    public DistributedTaskFactoryBeanRegistry distributedTaskFactoryBeanRegistry(final StroomBeanStore stroomBeanStore) {
        return new DistributedTaskFactoryBeanRegistry(stroomBeanStore);
    }

    @Bean
    public DistributedTaskFetcher distributedTaskFetcher(final TaskManager taskManager,
                                                         final JobNodeTrackerCache jobNodeTrackerCache,
                                                         final NodeCache nodeCache) {
        return new DistributedTaskFetcher(taskManager, jobNodeTrackerCache, nodeCache);
    }

    @Bean
    @Scope(value = StroomScope.TASK)
    public DistributedTaskRequestClusterHandler distributedTaskRequestClusterHandler(final DistributedTaskFactoryBeanRegistry distributedTaskFactoryBeanRegistry) {
return new DistributedTaskRequestClusterHandler(distributedTaskFactoryBeanRegistry);
    }

    @Bean
    @Scope(StroomScope.TASK)
    public FetchJobDataHandler fetchJobDataHandler(final JobService jobService,
                                                   final JobNodeService jobNodeService,
                                                   final ClusterDispatchAsyncHelper dispatchHelper) {
        return new FetchJobDataHandler(jobService, jobNodeService, dispatchHelper);
    }

    @Bean
    @Scope(value = StroomScope.TASK)
    public GetScheduledTimesHandler getScheduledTimesHandler(final ScheduleService scheduleService) {
        return new GetScheduledTimesHandler(scheduleService);
    }

    @Bean
    public JobManager jobManager(final JobService jobService,
                                 final JobNodeService jobNodeService,
                                 final LifecycleServiceImpl lifecycleService) {
        return new JobManagerImpl(jobService, jobNodeService, lifecycleService);
    }

    @Bean
    @Scope(value = StroomScope.TASK)
    public JobNodeInfoClusterHandler jobNodeInfoClusterHandler(final JobNodeTrackerCache jobNodeTrackerCache) {
        return new JobNodeInfoClusterHandler(jobNodeTrackerCache);
    }

    @Bean
    public JobNodeService jobNodeService(final StroomEntityManager entityManager,
                                         final ClusterLockService clusterLockService,
                                         final NodeCache nodeCache,
                                         final JobService jobService,
                                         final StroomBeanStore stroomBeanStore,
                                         final StroomDatabaseInfo stroomDatabaseInfo) {
        return new JobNodeServiceImpl(entityManager, clusterLockService, nodeCache, jobService, stroomBeanStore, stroomDatabaseInfo);
    }

    @Bean
    public JobNodeTrackerCache jobNodeTrackerCache(final NodeCache nodeCache,
                                                   final JobNodeService jobNodeService) {
        return new JobNodeTrackerCache(nodeCache, jobNodeService);
    }

    @Bean
    public JobService jobService(final StroomEntityManager entityManager,
                                 final StroomBeanStore stroomBeanStore) {
        return new JobServiceImpl(entityManager, stroomBeanStore);
    }

    @Bean
    public ScheduleService scheduleService() {
        return new ScheduleServiceImpl();
    }

    @Bean
    public ScheduledTaskExecutor scheduledTaskExecutor(final StroomBeanStore stroomBeanStore,
                                                       final JobNodeTrackerCache jobNodeTrackerCache,
                                                       final TaskManager taskManager) {
        return new ScheduledTaskExecutorImpl(stroomBeanStore, jobNodeTrackerCache, taskManager);
    }
}