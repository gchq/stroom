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

package stroom.task.impl;

import stroom.lifecycle.api.LifecycleBinder;
import stroom.searchable.api.Searchable;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.TaskManager;
import stroom.task.shared.TaskResource;
import stroom.util.RunnableWrapper;
import stroom.util.guice.GuiceUtil;
import stroom.util.guice.RestResourcesBinder;

import com.google.inject.AbstractModule;

import javax.inject.Inject;
import javax.servlet.http.HttpSessionListener;
import java.util.concurrent.Executor;

public class TaskModule extends AbstractModule {
    @Override
    protected void configure() {
        install(new TaskContextModule());

        bind(ExecutorProvider.class).to(ExecutorProviderImpl.class);
        bind(Executor.class).toProvider(ExecutorProviderImpl.class);
        bind(TaskContextFactory.class).to(TaskContextFactoryImpl.class);
        bind(TaskManager.class).to(TaskManagerImpl.class);
        bind(TaskResource.class).to(TaskResourceImpl.class);

        RestResourcesBinder.create(binder())
                .bind(TaskResourceImpl.class);

        GuiceUtil.buildMultiBinder(binder(), HttpSessionListener.class)
                .addBinding(TaskManagerSessionListener.class);

        GuiceUtil.buildMultiBinder(binder(), Searchable.class)
                .addBinding(SearchableTaskProgress.class);

        // Make sure the first thing to start and the last thing to stop is the task manager.
        LifecycleBinder.create(binder())
                .bindStartupTaskTo(TaskManagerStartup.class, Integer.MAX_VALUE)
                .bindShutdownTaskTo(TaskManagerShutdown.class, Integer.MAX_VALUE);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return 0;
    }

    private static class TaskManagerStartup extends RunnableWrapper {
        @Inject
        TaskManagerStartup(final TaskManager taskManager) {
            super(taskManager::startup);
        }
    }

    private static class TaskManagerShutdown extends RunnableWrapper {
        @Inject
        TaskManagerShutdown(final TaskManager taskManager) {
            super(taskManager::shutdown);
        }
    }
}