/*
 * Copyright 2016-2025 Crown Copyright
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
import jakarta.inject.Inject;

public class FsVolumeJobsModule extends AbstractModule {
    @Override
    protected void configure() {
        super.configure();

        ScheduledJobsBinder.create(binder())
                .bindJobTo(FileVolumeStatus.class, builder -> builder
                        .name("File System Volume Status")
                        .description("Update the usage status of file system volumes")
                        .frequencySchedule("5m"));
    }

    private static class FileVolumeStatus extends RunnableWrapper {
        @Inject
        FileVolumeStatus(final FsVolumeService volumeService) {
            super(volumeService::updateStatus);
        }
    }
}
