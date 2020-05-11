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

import stroom.job.api.RunnableWrapper;
import stroom.job.api.ScheduledJobsBinder;

import com.google.inject.AbstractModule;

import javax.inject.Inject;

import static stroom.job.api.Schedule.ScheduleType.CRON;

public class FsDataStoreJobsModule extends AbstractModule {
    @Override
    protected void configure() {

        ScheduledJobsBinder.create(binder())
                .bindJobTo(FileSystemClean.class, builder -> builder
                        .withName("File System Clean")
                        .withDescription("Job to process a volume deleting files that are no " +
                                "longer indexed (maybe the retention period has past or they have been deleted)")
                        .withSchedule(CRON, "0 0 *")
                        .withAdvancedState(false))
                .bindJobTo(MetaDelete.class, builder -> builder
                        .withName("Meta Delete")
                        .withDescription("Physically delete streams that have been logically deleted " +
                                "based on age of delete (stroom.data.store.deletePurgeAge)")
                        .withSchedule(CRON, "0 0 *"));
    }

    private static class FileSystemClean extends RunnableWrapper {
        @Inject
        FileSystemClean(final FsCleanExecutor fileSystemCleanExecutor) {
            super(fileSystemCleanExecutor::clean);
        }
    }

    private static class MetaDelete extends RunnableWrapper {
        @Inject
        MetaDelete(final PhysicalDeleteExecutor physicalDeleteExecutor) {
            super(physicalDeleteExecutor::exec);
        }
    }
}