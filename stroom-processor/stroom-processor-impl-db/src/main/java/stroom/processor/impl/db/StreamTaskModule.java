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
import stroom.entity.FindService;
import stroom.job.api.DistributedTaskFactory;
import stroom.processor.StreamProcessorFilterService;
import stroom.processor.StreamProcessorService;
import stroom.processor.shared.task.CreateProcessorAction;
import stroom.processor.shared.task.FetchProcessorAction;
import stroom.processor.shared.task.ReprocessDataAction;
import stroom.task.api.TaskHandlerBinder;
import stroom.util.GuiceUtil;
import stroom.util.RestResource;

public class StreamTaskModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(StreamTaskCreator.class).to(StreamTaskCreatorImpl.class);
        bind(StreamProcessorFilterService.class).to(StreamProcessorFilterServiceImpl.class);
        bind(StreamProcessorService.class).to(StreamProcessorServiceImpl.class);
        bind(StreamTaskService.class).to(StreamTaskServiceImpl.class);
        bind(CachedStreamProcessorFilterService.class).to(CachedStreamProcessorFilterServiceImpl.class);
        bind(CachedStreamProcessorService.class).to(CachedStreamProcessorServiceImpl.class);

        TaskHandlerBinder.create(binder())
                .bind(CreateProcessorAction.class, stroom.processor.impl.db.task.CreateProcessorHandler.class)
                .bind(CreateStreamTasksTask.class, stroom.processor.impl.db.task.CreateStreamTasksTaskHandler.class)
                .bind(FetchProcessorAction.class, stroom.processor.impl.db.task.FetchProcessorHandler.class)
                .bind(ReprocessDataAction.class, ReprocessDataHandler.class)
                .bind(StreamProcessorTask.class, stroom.processor.impl.db.task.StreamProcessorTaskHandler.class);

        GuiceUtil.buildMultiBinder(binder(), DistributedTaskFactory.class)
                .addBinding(StreamProcessorTaskFactory.class);

        GuiceUtil.buildMultiBinder(binder(), FindService.class)
                .addBinding(StreamTaskServiceImpl.class);

        GuiceUtil.buildMultiBinder(binder(), RestResource.class)
                .addBinding(StreamTaskResource.class);
    }
}