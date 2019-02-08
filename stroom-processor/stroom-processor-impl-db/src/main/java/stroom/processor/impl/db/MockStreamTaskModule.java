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

package stroom.processor.impl.db;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import stroom.entity.shared.Clearable;
import stroom.processor.StreamProcessorFilterService;
import stroom.processor.StreamProcessorService;
import stroom.processor.impl.db.task.StreamProcessorTaskHandler;
import stroom.task.api.TaskHandlerBinder;

public class MockStreamTaskModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(StreamTaskCreator.class).to(MockStreamTaskCreator.class);
        bind(StreamProcessorService.class).to(MockStreamProcessorService.class);
        bind(StreamProcessorFilterService.class).to(MockStreamProcessorFilterService.class);
        bind(StreamTaskService.class).to(MockStreamTaskService.class);

        bind(CachedStreamProcessorService.class).to(MockStreamProcessorService.class);
        bind(CachedStreamProcessorFilterService.class).to(MockStreamProcessorFilterService.class);

        final Multibinder<Clearable> clearableBinder = Multibinder.newSetBinder(binder(), Clearable.class);
        clearableBinder.addBinding().to(MockStreamProcessorService.class);
        clearableBinder.addBinding().to(MockStreamProcessorFilterService.class);
        clearableBinder.addBinding().to(MockStreamTaskService.class);

        TaskHandlerBinder.create(binder())
                .bind(StreamProcessorTask.class, StreamProcessorTaskHandler.class);
    }
}