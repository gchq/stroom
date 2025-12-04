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

package stroom.pipeline.refdata;

import stroom.pipeline.errorhandler.StoredErrorReceiver;
import stroom.pipeline.refdata.store.RefStreamDefinition;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.function.Function;
import java.util.function.Supplier;

public class ReferenceDataLoaderImpl implements ReferenceDataLoader {

    private final TaskContextFactory taskContextFactory;
    private final Provider<ReferenceDataLoadTaskHandler> taskHandlerProvider;

    @Inject
    ReferenceDataLoaderImpl(final TaskContextFactory taskContextFactory,
                            final Provider<ReferenceDataLoadTaskHandler> taskHandlerProvider) {
        this.taskContextFactory = taskContextFactory;
        this.taskHandlerProvider = taskHandlerProvider;
    }

    @Override
    public StoredErrorReceiver load(final RefStreamDefinition refStreamDefinition) {
        final Function<TaskContext, StoredErrorReceiver> consumer = taskContext ->
                taskHandlerProvider
                        .get()
                        .exec(taskContext, refStreamDefinition);

        final Supplier<StoredErrorReceiver> supplier = taskContextFactory.childContextResult(
                taskContextFactory.current(),
                "Load Reference Data",
                consumer);
        return supplier.get();
    }
}
