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

import stroom.job.api.ScheduledJobsBinder;
import stroom.util.RunnableWrapper;

import com.google.inject.AbstractModule;

import javax.inject.Inject;

import static stroom.job.api.Schedule.ScheduleType.CRON;

public class FsDataStoreJobsModule extends AbstractModule {
    @Override
    protected void configure() {

        ScheduledJobsBinder.create(binder())
                .bindJobTo(PhysicalDelete.class, builder -> builder
                        .withName("Data Delete")
                        .withDescription("Physically delete meta data and associated files that have been logically deleted " +
                                "based on age of delete (stroom.data.store.deletePurgeAge)")
                        .withSchedule(CRON, "0 0 *")
                        .withAdvancedState(false))
                .bindJobTo(FileSystemClean.class, builder -> builder
                        .withName("File System Clean (deprecated)")
                        .withDescription("Job to process a volume deleting files that are no " +
                                "longer indexed (maybe the retention period has past or they have been deleted)")
                        .withSchedule(CRON, "0 0 *")
                        .withEnabledState(false));
    }

    private static class PhysicalDelete extends RunnableWrapper {
        @Inject
        PhysicalDelete(final PhysicalDeleteExecutor physicalDeleteExecutor) {
            super(physicalDeleteExecutor::exec);
        }
    }

    private static class FileSystemClean extends RunnableWrapper {
        @Inject
        FileSystemClean(final FsCleanExecutor fileSystemCleanExecutor) {
            super(fileSystemCleanExecutor::clean);
        }
    }
}