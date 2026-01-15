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

package stroom.search.impl;

import stroom.query.common.v2.Coprocessors;
import stroom.query.common.v2.ResultStore;
import stroom.security.api.SecurityContext;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.TerminateHandlerFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;

public class LocalNodeSearch implements NodeSearch {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(LocalNodeSearch.class);

    private final NodeSearchTaskHandlers nodeSearchTaskHandlers;
    private final SecurityContext securityContext;
    private final TaskContextFactory taskContextFactory;

    @Inject
    public LocalNodeSearch(final NodeSearchTaskHandlers nodeSearchTaskHandlers,
                           final SecurityContext securityContext,
                           final TaskContextFactory taskContextFactory) {
        this.nodeSearchTaskHandlers = nodeSearchTaskHandlers;
        this.securityContext = securityContext;
        this.taskContextFactory = taskContextFactory;
    }

    @Override
    public void searchNode(final String sourceNode,
                           final String targetNode,
                           final FederatedSearchTask task,
                           final NodeSearchTask nodeSearchTask,
                           final TaskContext parentContext) {
        LOGGER.debug(() -> task.getSearchName() + " - searching node: " + targetNode + "...");
        parentContext.info(() -> task.getSearchName() + " - searching node: " + targetNode + "...");
        final ResultStore resultStore = task.getResultStore();

        // Start local cluster search execution.
        final Coprocessors coprocessors = resultStore.getCoprocessors();
        LOGGER.debug(() -> "Dispatching node search task to node: " + targetNode);
        try {
            LOGGER.debug(() -> "startSearch " + nodeSearchTask);
            securityContext.useAsRead(() -> {
                if (coprocessors != null && coprocessors.isPresent()) {
                    final NodeSearchTaskHandler nodeSearchTaskHandler =
                            nodeSearchTaskHandlers.get(nodeSearchTask.getType());

                    // Add a child context just to get the same indentation level for local and remote search tasks.
                    taskContextFactory.childContext(
                            parentContext,
                            nodeSearchTask.getTaskName(),
                            TerminateHandlerFactory.NOOP_FACTORY,
                            taskContext ->
                                    nodeSearchTaskHandler.search(taskContext,
                                            nodeSearchTask,
                                            coprocessors)).run();

                } else {
                    throw new SearchException("No coprocessors were created");
                }
            });
        } catch (final Throwable e) {
            LOGGER.debug(e::getMessage, e);
            LOGGER.debug(() -> "Failed to start local search on node: " + targetNode);
            final SearchException searchException = new SearchException(
                    "Failed to start local search on node: " + targetNode, e);
            resultStore.onFailure(targetNode, searchException);
            throw searchException;
        }
    }
}
