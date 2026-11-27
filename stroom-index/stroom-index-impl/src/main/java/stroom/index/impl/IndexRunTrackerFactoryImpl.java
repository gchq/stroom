/*
 * Copyright 2026 Crown Copyright
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

package stroom.index.impl;

import stroom.processor.api.HeldProcessorTaskService;
import stroom.processor.api.RunIndexing;
import stroom.processor.shared.ProcessorTask;
import stroom.security.api.SecurityContext;

import jakarta.inject.Inject;

class IndexRunTrackerFactoryImpl implements IndexRunTrackerFactory {

    private final IndexShardTaskDao taskDao;
    private final HeldProcessorTaskService heldProcessorTaskService;
    private final SecurityContext securityContext;
    private final IndexShardPurger purger;
    private final IndexRunRegistry runRegistry;

    /// Creates the factory.
    ///
    /// @param taskDao                  Reads and writes the runs' rows.
    /// @param heldProcessorTaskService Reads the status of the tasks.
    /// @param securityContext          Records as the processing user.
    /// @param purger                   Purges a stream before a run first writes it to a shard.
    /// @param runRegistry              Holds the runs on this node, so that replaced ones can be stopped.
    @Inject
    IndexRunTrackerFactoryImpl(final IndexShardTaskDao taskDao,
                               final HeldProcessorTaskService heldProcessorTaskService,
                               final SecurityContext securityContext,
                               final IndexShardPurger purger,
                               final IndexRunRegistry runRegistry) {
        this.taskDao = taskDao;
        this.heldProcessorTaskService = heldProcessorTaskService;
        this.securityContext = securityContext;
        this.purger = purger;
        this.runRegistry = runRegistry;
    }

    @Override
    public IndexRunTracker create(final ProcessorTask task, final RunIndexing runIndexing) {
        final TaskIndexRunTracker tracker = new TaskIndexRunTracker(
                taskDao, heldProcessorTaskService, securityContext, task, runIndexing, purger);
        runRegistry.add(tracker);
        return tracker;
    }
}
