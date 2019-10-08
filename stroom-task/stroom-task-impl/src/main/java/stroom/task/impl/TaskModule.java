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

import com.google.inject.AbstractModule;
import stroom.searchable.api.Searchable;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.GenericServerTask;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskHandlerBinder;
import stroom.task.api.TaskManager;
import stroom.task.shared.FindTaskProgressAction;
import stroom.task.shared.FindUserTaskProgressAction;
import stroom.task.shared.TerminateTaskProgressAction;
import stroom.util.guice.GuiceUtil;

import javax.servlet.http.HttpSessionListener;

public class TaskModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(ExecutorProvider.class).to(ExecutorProviderImpl.class);
        bind(TaskManager.class).to(TaskManagerImpl.class);
        bind(TaskContext.class).to(TaskContextImpl.class);

        TaskHandlerBinder.create(binder())
                .bind(FindTaskProgressAction.class, FindTaskProgressHandler.class)
                .bind(FindTaskProgressClusterTask.class, FindTaskProgressClusterHandler.class)
                .bind(FindUserTaskProgressAction.class, FindUserTaskProgressHandler.class)
                .bind(GenericServerTask.class, GenericServerTaskHandler.class)
                .bind(TerminateTaskProgressAction.class, TerminateTaskProgressHandler.class);

        GuiceUtil.buildMultiBinder(binder(), HttpSessionListener.class)
                .addBinding(TaskManagerSessionListener.class);

        GuiceUtil.buildMultiBinder(binder(), Searchable.class)
                .addBinding(SearchableTaskProgress.class);
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
}