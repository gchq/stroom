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

package stroom.processor.impl;

import com.google.inject.AbstractModule;
import stroom.job.api.DistributedTaskFactory;
import stroom.processor.api.ProcessorFilterService;
import stroom.processor.api.ProcessorFilterTaskService;
import stroom.processor.api.ProcessorService;
import stroom.processor.shared.CreateProcessorFilterAction;
import stroom.processor.shared.FetchProcessorAction;
import stroom.processor.shared.ReprocessDataAction;
import stroom.task.api.TaskHandlerBinder;
import stroom.util.GuiceUtil;
import stroom.util.RestResource;
import stroom.util.shared.Clearable;

public class ProcessorModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(ProcessorFilterTaskManager.class).to(ProcessorFilterTaskManagerImpl.class);
        bind(ProcessorFilterService.class).to(ProcessorFilterServiceImpl.class);
        bind(ProcessorService.class).to(ProcessorServiceImpl.class);
        bind(ProcessorFilterTaskService.class).to(ProcessorFilterTaskServiceImpl.class);
//        bind(ProcessorFilterTaskService.class).to(StreamTaskServiceImpl.class);

        TaskHandlerBinder.create(binder())
                .bind(CreateProcessorFilterAction.class, CreateProcessorHandler.class)
                .bind(CreateStreamTasksTask.class, CreateStreamTasksTaskHandler.class)
                .bind(FetchProcessorAction.class, FetchProcessorHandler.class)
                .bind(ReprocessDataAction.class, ReprocessDataHandler.class)
                .bind(DataProcessorTask.class, DataProcessorTaskHandler.class);

        GuiceUtil.buildMultiBinder(binder(), DistributedTaskFactory.class)
                .addBinding(DataProcessorTaskFactory.class);

        GuiceUtil.buildMultiBinder(binder(), Clearable.class).addBinding(ProcessorCache.class);

//        GuiceUtil.buildMultiBinder(binder(), FindService.class)
//                .addBinding(StreamTaskServiceImpl.class);

        GuiceUtil.buildMultiBinder(binder(), RestResource.class)
                .addBinding(StreamTaskResource.class);
    }
}