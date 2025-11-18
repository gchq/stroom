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

import stroom.docstore.api.DocumentActionHandlerBinder;
import stroom.importexport.api.ImportExportActionHandler;
import stroom.job.api.DistributedTaskFactory;
import stroom.job.api.ScheduledJobsBinder;
import stroom.lifecycle.api.LifecycleBinder;
import stroom.processor.api.ProcessorFilterService;
import stroom.processor.api.ProcessorService;
import stroom.processor.api.ProcessorTaskService;
import stroom.processor.shared.ProcessorFilterDoc;
import stroom.processor.shared.ProcessorResource;
import stroom.processor.shared.ProcessorTaskResource;
import stroom.query.api.datasource.DataSourceProvider;
import stroom.searchable.api.Searchable;
import stroom.util.RunnableWrapper;
import stroom.util.guice.GuiceUtil;
import stroom.util.guice.HasSystemInfoBinder;
import stroom.util.guice.RestResourcesBinder;
import stroom.util.shared.Clearable;
import stroom.util.shared.HasUserDependencies;

import com.google.inject.AbstractModule;
import jakarta.inject.Inject;

public class ProcessorModule extends AbstractModule {

    public static final String PROCESSOR_TASK_RETENTION_JOB_NAME = "Processor Task Retention";

    @Override
    protected void configure() {
        bind(ProcessorTaskQueueManager.class).to(ProcessorTaskQueueManagerImpl.class);
        bind(ProcessorTaskCreator.class).to(ProcessorTaskCreatorImpl.class);
        bind(ProcessorFilterService.class).to(ProcessorFilterServiceImpl.class);
        bind(ProcessorService.class).to(ProcessorServiceImpl.class);
        bind(ProcessorResource.class).to(ProcessorResourceImpl.class);
        bind(ProcessorTaskResource.class).to(ProcessorTaskResourceImpl.class);
        bind(ProcessorTaskService.class).to(ProcessorTaskServiceImpl.class);

        RestResourcesBinder.create(binder())
                .bind(ProcessorResourceImpl.class)
                .bind(ProcessorFilterResourceImpl.class)
                .bind(ProcessorTaskResourceImpl.class);

        GuiceUtil.buildMultiBinder(binder(), DistributedTaskFactory.class)
                .addBinding(DataProcessorTaskFactory.class);

        GuiceUtil.buildMultiBinder(binder(), Clearable.class)
                .addBinding(ProcessorFilterCache.class)
                .addBinding(PrioritisedFilters.class);

        GuiceUtil.buildMultiBinder(binder(), DataSourceProvider.class)
                .addBinding(ProcessorTaskServiceImpl.class);
        GuiceUtil.buildMultiBinder(binder(), Searchable.class)
                .addBinding(ProcessorTaskServiceImpl.class);

        GuiceUtil.buildMultiBinder(binder(), ImportExportActionHandler.class)
                .addBinding(ProcessorFilterImportExportHandlerImpl.class);

        GuiceUtil.buildMapBinder(binder(), String.class, HasUserDependencies.class)
                .addBinding(ProcessorFilterServiceImpl.class.getName(), ProcessorFilterServiceImpl.class);

        DocumentActionHandlerBinder.create(binder())
                .bind(ProcessorFilterDoc.TYPE, ProcessorFilterImportExportHandlerImpl.class);

        HasSystemInfoBinder.create(binder())
                .bind(ProcessorTaskQueueManagerImpl.class);

        ScheduledJobsBinder.create(binder())
                .bindJobTo(ProcessorTaskQueueStatistics.class, builder -> builder
                        .name("Processor Task Queue Statistics")
                        .description("Write statistics about the size of the task queue")
                        .frequencySchedule("1m"))
                .bindJobTo(ProcessorTaskRetention.class, builder -> builder
                        .name(PROCESSOR_TASK_RETENTION_JOB_NAME)
                        .description("Physically delete processor tasks that have been logically " +
                                     "deleted or complete based on age (stroom.processor.deleteAge)")
                        .frequencySchedule("10m"))
                .bindJobTo(ProcessorTaskManagerDisownDeadTasks.class, builder -> builder
                        .name("Processor Task Manager Disown Dead Tasks")
                        .description("Tasks that seem to be stuck processing due to the death of a processing node " +
                                     "are disowned and added back to the task queue for processing after " +
                                     "(stroom.processor.disownDeadTasksAfter)")
                        .frequencySchedule("1m"))
                .bindJobTo(ProcessorTaskManagerReleaseOldQueuedTasks.class, builder -> builder
                        .name("Processor Task Manager Release Old Queued Tasks")
                        .description("Release queued tasks from old master nodes")
                        .frequencySchedule("1m"))
                .bindJobTo(ProcessorTaskCreatorJob.class, builder -> builder
                        .name("Processor Task Creator")
                        .description("Create Processor Tasks from Processor Filters")
                        .frequencySchedule("10s")
                        .enabledOnBootstrap(true) // We want processing to start in a test env
                        .enabled(false)
                        .advanced(false));

        LifecycleBinder.create(binder())
                .bindStartupTaskTo(ProcessorTaskManagerStartup.class)
                .bindShutdownTaskTo(ProcessorTaskManagerShutdown.class);
    }


    // --------------------------------------------------------------------------------


    private static class ProcessorTaskQueueStatistics extends RunnableWrapper {

        @Inject
        ProcessorTaskQueueStatistics(final ProcessorTaskQueueManager processorTaskQueueManager) {
            super(processorTaskQueueManager::writeQueueStatistics);
        }
    }


    // --------------------------------------------------------------------------------


    private static class ProcessorTaskRetention extends RunnableWrapper {

        @Inject
        ProcessorTaskRetention(final ProcessorTaskDeleteExecutor processorTaskDeleteExecutor) {
            super(processorTaskDeleteExecutor::exec);
        }
    }


    // --------------------------------------------------------------------------------


    private static class ProcessorTaskManagerStartup extends RunnableWrapper {

        @Inject
        ProcessorTaskManagerStartup(final ProcessorTaskQueueManagerImpl processorTaskManager) {
            super(processorTaskManager::startup);
        }
    }


    // --------------------------------------------------------------------------------


    private static class ProcessorTaskManagerShutdown extends RunnableWrapper {

        @Inject
        ProcessorTaskManagerShutdown(final ProcessorTaskQueueManagerImpl processorTaskManager) {
            super(processorTaskManager::shutdown);
        }
    }


    // --------------------------------------------------------------------------------


    private static class ProcessorTaskManagerDisownDeadTasks extends RunnableWrapper {

        @Inject
        ProcessorTaskManagerDisownDeadTasks(final ProcessorTaskQueueManagerImpl processorTaskManager) {
            super(processorTaskManager::disownDeadTasks);
        }
    }


    // --------------------------------------------------------------------------------


    private static class ProcessorTaskManagerReleaseOldQueuedTasks extends RunnableWrapper {

        @Inject
        ProcessorTaskManagerReleaseOldQueuedTasks(final ProcessorTaskQueueManagerImpl processorTaskQueueManager) {
            super(processorTaskQueueManager::releaseOldQueuedTasks);
        }
    }


    // --------------------------------------------------------------------------------


    private static class ProcessorTaskCreatorJob extends RunnableWrapper {

        @Inject
        ProcessorTaskCreatorJob(final ProcessorTaskCreator processorTaskCreator) {
            super(processorTaskCreator::exec);
        }
    }
}
