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

package stroom.streamstore.store.impl.fs;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import stroom.streamstore.store.StreamMaintenanceService;
import stroom.streamstore.store.api.StreamStore;
import stroom.task.TaskHandler;

public class FSModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(StreamMaintenanceService.class).to(FileSystemStreamMaintenanceService.class);
        bind(StreamStore.class).to(FileSystemStreamStoreImpl.class);

        final Multibinder<TaskHandler> taskHandlerBinder = Multibinder.newSetBinder(binder(), TaskHandler.class);
        taskHandlerBinder.addBinding().to(stroom.streamstore.store.impl.fs.FileSystemCleanSubTaskHandler.class);
    }
}