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

package stroom.task.impl;

import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskManager;

import com.google.inject.AbstractModule;

import java.util.concurrent.Executor;

public class MockTaskModule extends AbstractModule {

    @Override
    protected void configure() {
        install(new TaskContextModule());
        bind(ExecutorProvider.class).to(ExecutorProviderImpl.class);
        bind(Executor.class).toProvider(ExecutorProviderImpl.class);
        bind(TaskManager.class).to(TaskManagerImpl.class);
    }
}
