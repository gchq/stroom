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

import stroom.importexport.api.ImportExportActionHandler;
import stroom.job.api.DistributedTaskFactory;
import stroom.job.api.RunnableWrapper;
import stroom.job.api.ScheduledJobsBinder;
import stroom.processor.api.ProcessorFilterService;
import stroom.processor.api.ProcessorService;
import stroom.processor.api.ProcessorTaskService;
import stroom.processor.shared.ProcessorResource;
import stroom.processor.shared.ProcessorTaskResource;
import stroom.searchable.api.Searchable;
import stroom.util.guice.GuiceUtil;
import stroom.util.guice.RestResourcesBinder;
import stroom.util.shared.Clearable;

import com.google.inject.AbstractModule;

import javax.inject.Inject;

import static stroom.job.api.Schedule.ScheduleType.PERIODIC;

public class ProcessorModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(ProcessorTaskManager.class).to(ProcessorTaskManagerImpl.class);
        bind(ProcessorFilterService.class).to(ProcessorFilterServiceImpl.class);
        bind(ProcessorService.class).to(ProcessorServiceImpl.class);
        bind(ProcessorResource.class).to(ProcessorResourceImpl.class);
        bind(ProcessorTaskResource.class).to(ProcessorTaskResourceImpl.class);
        bind(ProcessorTaskService.class).to(ProcessorTaskServiceImpl.class);

        RestResourcesBinder.create(binder())
                .bind(ProcessorResourceImpl.class)
                .bind(ProcessorFilterResourceImpl.class)
                .bind(ProcessorTaskResourceImpl.class)
                .bind(StreamTaskResource.class);

        GuiceUtil.buildMultiBinder(binder(), DistributedTaskFactory.class)
                .addBinding(DataProcessorTaskFactory.class);

        GuiceUtil.buildMultiBinder(binder(), Clearable.class)
                .addBinding(ProcessorCache.class)
                .addBinding(ProcessorFilterCache.class);

        GuiceUtil.buildMultiBinder(binder(), Searchable.class)
                .addBinding(ProcessorTaskServiceImpl.class);

        GuiceUtil.buildMultiBinder(binder(), ImportExportActionHandler.class)
                .addBinding(ProcessorFilterImportExportHandlerImpl.class);

        ScheduledJobsBinder.create(binder())
                .bindJobTo(ProcessorTaskQueueStatistics.class, builder -> builder
                        .withName("Processor Task Queue Statistics")
                        .withDescription("Write statistics about the size of the task queue")
                        .withSchedule(PERIODIC, "1m"))
                .bindJobTo(ProcessorTaskRetention.class, builder -> builder
                        .withName("Processor Task Retention")
                        .withDescription("Physically delete processor tasks that have been logically " +
                                "deleted or complete based on age (stroom.process.deletePurgeAge)")
                        .withSchedule(PERIODIC, "1m"));
    }

    private static class ProcessorTaskQueueStatistics extends RunnableWrapper {
        @Inject
        ProcessorTaskQueueStatistics(final ProcessorTaskManager processorTaskManager) {
            super(processorTaskManager::writeQueueStatistics);
        }
    }

    private static class ProcessorTaskRetention extends RunnableWrapper {
        @Inject
        ProcessorTaskRetention(final ProcessorTaskDeleteExecutor processorTaskDeleteExecutor) {
            super(processorTaskDeleteExecutor::exec);
        }
    }
}