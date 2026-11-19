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

package stroom.node.client.component;

import stroom.ui.config.shared.NodeMonitoringConfig;

/// A node's latest ping: how long it took, or why it failed, with the thresholds it is judged by.
@SuppressWarnings("ClassCanBeRecord")
public final class NodePing {

    private final Long ping;
    private final String error;
    private final NodeMonitoringConfig nodeMonitoringConfig;

    private NodePing(final Long ping,
                     final String error,
                     final NodeMonitoringConfig nodeMonitoringConfig) {
        this.ping = ping;
        this.error = error;
        this.nodeMonitoringConfig = nodeMonitoringConfig;
    }

    /// @param ping                 How long the node took to answer, in milliseconds.
    /// @param nodeMonitoringConfig The ping thresholds.
    /// @return A ping the node answered.
    public static NodePing success(final Long ping, final NodeMonitoringConfig nodeMonitoringConfig) {
        return new NodePing(ping, null, nodeMonitoringConfig);
    }

    /// @param error                Why the node didn't answer.
    /// @param nodeMonitoringConfig The ping thresholds.
    /// @return A ping the node didn't answer.
    public static NodePing error(final String error, final NodeMonitoringConfig nodeMonitoringConfig) {
        return new NodePing(null, error, nodeMonitoringConfig);
    }

    /// @return How long the node took to answer, in milliseconds, or `null` if it didn't.
    public Long getPing() {
        return ping;
    }

    /// @return Why the node didn't answer, or `null` if it did.
    public String getError() {
        return error;
    }

    /// @return The ping thresholds.
    public NodeMonitoringConfig getNodeMonitoringConfig() {
        return nodeMonitoringConfig;
    }
}
