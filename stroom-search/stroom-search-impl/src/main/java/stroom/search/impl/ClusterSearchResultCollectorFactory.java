/*
 * Copyright 2017 Crown Copyright
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

package stroom.search.impl;

import stroom.query.common.v2.Coprocessors;
import stroom.task.api.TaskContextFactory;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.Set;
import java.util.concurrent.Executor;

public class ClusterSearchResultCollectorFactory {
    private final Executor executor;
    private final TaskContextFactory taskContextFactory;
    private final Provider<AsyncSearchTaskHandler> asyncSearchTaskHandlerProvider;

    @Inject
    private ClusterSearchResultCollectorFactory(final Executor executor,
                                                final TaskContextFactory taskContextFactory,
                                                final Provider<AsyncSearchTaskHandler> asyncSearchTaskHandlerProvider) {
        this.executor = executor;
        this.taskContextFactory = taskContextFactory;
        this.asyncSearchTaskHandlerProvider = asyncSearchTaskHandlerProvider;
    }

    public ClusterSearchResultCollector create(final AsyncSearchTask task,
                                               final String nodeName,
                                               final Set<String> highlights,
                                               final Coprocessors coprocessors) {
        return new ClusterSearchResultCollector(executor,
                taskContextFactory,
                asyncSearchTaskHandlerProvider,
                task,
                nodeName,
                highlights,
                coprocessors);
    }
}
