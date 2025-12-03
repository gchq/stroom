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

package stroom.resource.impl;

import stroom.job.api.ScheduledJobsBinder;
import stroom.lifecycle.api.LifecycleBinder;
import stroom.resource.api.ResourceStore;
import stroom.util.RunnableWrapper;
import stroom.util.guice.ServletBinder;

import com.google.inject.AbstractModule;
import jakarta.inject.Inject;

public class ResourceModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(ResourceStore.class).to(UserResourceStoreImpl.class);

        ServletBinder.create(binder())
                .bind(UserResourceStoreImpl.class);

        ScheduledJobsBinder.create(binder())
                .bindJobTo(DeleteTempFile.class, builder -> builder
                        .name("Delete temp resource files")
                        .description("Deletes resource store temporary files that are over one hour old.")
                        .managed(false)
                        .frequencySchedule("10m"));

        LifecycleBinder.create(binder())
                .bindStartupTaskTo(ResourceStoreStartup.class)
                .bindShutdownTaskTo(ResourceStoreShutdown.class);
    }


    // --------------------------------------------------------------------------------


    private static class DeleteTempFile extends RunnableWrapper {

        @Inject
        DeleteTempFile(final UserResourceStoreImpl resourceStore) {
            super(resourceStore::execute);
        }
    }


    // --------------------------------------------------------------------------------


    private static class ResourceStoreStartup extends RunnableWrapper {

        @Inject
        ResourceStoreStartup(final UserResourceStoreImpl resourceStore) {
            super(resourceStore::startup);
        }
    }


    // --------------------------------------------------------------------------------


    private static class ResourceStoreShutdown extends RunnableWrapper {

        @Inject
        ResourceStoreShutdown(final UserResourceStoreImpl resourceStore) {
            super(resourceStore::shutdown);
        }
    }
}
