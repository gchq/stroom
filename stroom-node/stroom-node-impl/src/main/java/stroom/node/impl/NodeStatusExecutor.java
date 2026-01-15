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

package stroom.node.impl;

import stroom.statistics.api.InternalStatisticsReceiver;
import stroom.task.api.TaskContextFactory;

import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class NodeStatusExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(NodeStatusExecutor.class);

    private final NodeStatusServiceUtil nodeStatusServiceUtil;
    private final InternalStatisticsReceiver internalStatisticsReceiver;
    private final TaskContextFactory taskContextFactory;

    @Inject
    NodeStatusExecutor(final NodeStatusServiceUtil nodeStatusServiceUtil,
                       final InternalStatisticsReceiver internalStatisticsReceiver,
                       final TaskContextFactory taskContextFactory) {
        this.nodeStatusServiceUtil = nodeStatusServiceUtil;
        this.internalStatisticsReceiver = internalStatisticsReceiver;
        this.taskContextFactory = taskContextFactory;
    }

    /**
     * Gets a task if one is available, returns null otherwise.
     *
     * @return A task.
     */
    void exec() {
        LOGGER.debug("Updating the status for this node.");
        taskContextFactory.current().info(() -> "Updating the status for this node");
        internalStatisticsReceiver.putEvents(nodeStatusServiceUtil.buildNodeStatus());
    }
}
