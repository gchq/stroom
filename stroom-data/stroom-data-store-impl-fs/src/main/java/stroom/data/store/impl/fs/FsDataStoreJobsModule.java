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

package stroom.data.store.impl.fs;

import stroom.cluster.lock.api.ClusterLockService;
import stroom.job.api.ScheduledJobsBinder;
import stroom.util.RunnableWrapper;
import stroom.util.shared.scheduler.CronExpressions;

import com.google.inject.AbstractModule;
import jakarta.inject.Inject;

public class FsDataStoreJobsModule extends AbstractModule {

    @Override
    protected void configure() {

        ScheduledJobsBinder.create(binder())
                .bindJobTo(DataDelete.class, builder -> builder
                        .name(PhysicalDeleteExecutor.TASK_NAME)
                        .description("Physically delete meta data and associated files that have been logically " +
                                     "deleted based on age of delete (stroom.data.store.deletePurgeAge)")
                        .cronSchedule(CronExpressions.EVERY_DAY_AT_MIDNIGHT.getExpression())
                        .advanced(false))
                .bindJobTo(OrphanFileFinder.class, builder -> builder
                        .name(FsOrphanFileFinderExecutor.TASK_NAME)
                        .description("Job to find files that do not exist in the meta store")
                        .cronSchedule(CronExpressions.EVERY_DAY_AT_MIDNIGHT.getExpression())
                        .enabledOnBootstrap(false)
                        .enabled(false))
                .bindJobTo(OrphanMetaFinder.class, builder -> builder
                        .name(FsOrphanMetaFinderExecutor.TASK_NAME)
                        .description("Job to find items in the meta store that have no associated data")
                        .cronSchedule(CronExpressions.EVERY_DAY_AT_MIDNIGHT.getExpression())
                        .enabledOnBootstrap(false)
                        .enabled(false));
    }


    // --------------------------------------------------------------------------------


    private static class DataDelete extends RunnableWrapper {

        @Inject
        DataDelete(final PhysicalDeleteExecutor physicalDeleteExecutor, final ClusterLockService clusterLockService) {
            super(() -> clusterLockService.tryLock("Data Delete", physicalDeleteExecutor::exec));
        }
    }


    // --------------------------------------------------------------------------------


    private static class OrphanFileFinder extends RunnableWrapper {

        @Inject
        OrphanFileFinder(final FsOrphanFileFinderExecutor executor, final ClusterLockService clusterLockService) {
            super(() -> clusterLockService.tryLock(FsOrphanFileFinderExecutor.TASK_NAME, executor::scan));
        }
    }


    // --------------------------------------------------------------------------------


    private static class OrphanMetaFinder extends RunnableWrapper {

        @Inject
        OrphanMetaFinder(final FsOrphanMetaFinderExecutor executor, final ClusterLockService clusterLockService) {
            super(() -> clusterLockService.tryLock(FsOrphanMetaFinderExecutor.TASK_NAME, executor::scan));
        }
    }
}
