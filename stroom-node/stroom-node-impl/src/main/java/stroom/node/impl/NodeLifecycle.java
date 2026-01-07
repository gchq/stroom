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

package stroom.node.impl;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import io.dropwizard.lifecycle.Managed;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

@Singleton
public class NodeLifecycle implements Managed {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(NodeLifecycle.class);

    private EtcdMembership membership;
    private final Provider<NodeConfig> nodeConfigProvider;

    @Inject
    public NodeLifecycle(final Provider<NodeConfig> nodeConfigProvider) {
        this.nodeConfigProvider = nodeConfigProvider;
    }

    @Override
    public synchronized void start() throws Exception {
        if (membership == null) {
            final NodeConfig config = nodeConfigProvider.get();

            final String nodeId = "node-" + config.getNodeName();
            membership = new EtcdMembership("http://localhost:2379", nodeId);

            // Register this node
            final String nodeInfo = String.format(
                    "{\"id\":\"%s\",\"host\":\"localhost\",\"port\":8080}",
                    nodeId
            );
            membership.registerNode(nodeInfo);

            // Watch for changes in background
            new Thread(membership::watchMembership).start();

            // List current members
            membership.printMembers();
        }
    }

    @Override
    public synchronized void stop() throws Exception {
        if (membership != null) {
            try {
                membership.unregister();
                membership.close();
            } catch (final Exception e) {
                LOGGER.error(e::getMessage, e);
            }
        }
    }
}
