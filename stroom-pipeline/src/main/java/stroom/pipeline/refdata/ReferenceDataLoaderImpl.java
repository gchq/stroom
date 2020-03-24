/*
 * Copyright 2016 Crown Copyright
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

package stroom.pipeline.refdata;

import stroom.pipeline.refdata.store.RefStreamDefinition;
import stroom.task.api.TaskContext;

import javax.inject.Inject;
import javax.inject.Provider;

public class ReferenceDataLoaderImpl implements ReferenceDataLoader {
    private final Provider<TaskContext> taskContextProvider;
    private final Provider<ReferenceDataLoadTaskHandler> taskHandlerProvider;

    @Inject
    ReferenceDataLoaderImpl(final Provider<TaskContext> taskContextProvider,
                            final Provider<ReferenceDataLoadTaskHandler> taskHandlerProvider) {
        this.taskContextProvider = taskContextProvider;
        this.taskHandlerProvider = taskHandlerProvider;
    }

    @Override
    public void load(final RefStreamDefinition refStreamDefinition) {
        final TaskContext taskContext = taskContextProvider.get();
        Runnable runnable = () -> taskHandlerProvider
                .get()
                .exec(refStreamDefinition);
        runnable = taskContext.subTask(runnable);
        runnable.run();
    }
}
