/*
 * Copyright 2016-2026 Crown Copyright
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

import stroom.cluster.task.api.TargetNodeSetFactory;
import stroom.meta.api.MetaService;
import stroom.node.api.NodeInfo;
import stroom.security.api.SecurityContext;
import stroom.statistics.api.InternalStatisticsReceiver;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContextFactory;

import jakarta.inject.Provider;

/**
 * Builds a real {@link ProcessorTaskQueueManagerImpl} for tests that live outside this package.
 * <p>
 * {@code ProcessorTaskQueueManagerImpl} is package private and should stay that way - it is reached
 * through {@link ProcessorTaskQueueManager} everywhere else - so this exists rather than widening
 * production visibility for the sake of a benchmark. Used by the gh-5699 dispatch benchmark, which
 * has to drive the master queue path and the worker claiming path over the same data to compare
 * them.
 */
public final class ProcessorTaskQueueManagerTestFactory {

    private ProcessorTaskQueueManagerTestFactory() {
    }

    public static ProcessorTaskQueueManager create(
            final ProcessorTaskDao processorTaskDao,
            final ExecutorProvider executorProvider,
            final TaskContextFactory taskContextFactory,
            final NodeInfo nodeInfo,
            final Provider<ProcessorConfig> processorConfigProvider,
            final Provider<InternalStatisticsReceiver> internalStatisticsReceiverProvider,
            final MetaService metaService,
            final SecurityContext securityContext,
            final TargetNodeSetFactory targetNodeSetFactory,
            final PrioritisedFilters prioritisedFilters,
            final ProcessorProfileCache processorProfileCache,
            final FilterFetchBackoff filterFetchBackoff) {

        return new ProcessorTaskQueueManagerImpl(
                processorTaskDao,
                executorProvider,
                taskContextFactory,
                nodeInfo,
                processorConfigProvider,
                internalStatisticsReceiverProvider,
                metaService,
                securityContext,
                targetNodeSetFactory,
                prioritisedFilters,
                processorProfileCache,
                filterFetchBackoff);
    }
}
