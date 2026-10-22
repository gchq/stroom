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

package stroom.ai.impl;

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

/// Stroom's own MCP endpoint, `/mcp`, through which MCP clients (e.g. VS Code or Claude) use Stroom's tools as the
/// signed-in user, under their tool policies.
@JsonPropertyOrder(alphabetic = true)
public class McpEndpointConfig extends AbstractConfig implements IsStroomConfig {

    static final StroomDuration DEFAULT_FORM_TIMEOUT = StroomDuration.ofMinutes(10);
    static final int DEFAULT_MAX_FORMS_PER_NODE = 50;
    static final StroomDuration DEFAULT_APPROVAL_TIMEOUT = StroomDuration.ofMinutes(10);
    static final StroomDuration DEFAULT_KEEP_ALIVE_INTERVAL = StroomDuration.ofSeconds(20);
    static final StroomDuration DEFAULT_SESSION_RETENTION = StroomDuration.ofDays(1);

    private final boolean enabled;
    private final StroomDuration formTimeout;
    private final int maxFormsPerNode;
    private final StroomDuration approvalTimeout;
    private final StroomDuration keepAliveInterval;
    private final StroomDuration sessionRetention;

    public McpEndpointConfig() {
        enabled = true;
        formTimeout = DEFAULT_FORM_TIMEOUT;
        maxFormsPerNode = DEFAULT_MAX_FORMS_PER_NODE;
        approvalTimeout = DEFAULT_APPROVAL_TIMEOUT;
        keepAliveInterval = DEFAULT_KEEP_ALIVE_INTERVAL;
        sessionRetention = DEFAULT_SESSION_RETENTION;
    }

    @JsonCreator
    public McpEndpointConfig(@JsonProperty("enabled") final Boolean enabled,
                             @JsonProperty("formTimeout") final StroomDuration formTimeout,
                             @JsonProperty("maxFormsPerNode") final Integer maxFormsPerNode,
                             @JsonProperty("approvalTimeout") final StroomDuration approvalTimeout,
                             @JsonProperty("keepAliveInterval") final StroomDuration keepAliveInterval,
                             @JsonProperty("sessionRetention") final StroomDuration sessionRetention) {
        this.enabled = Objects.requireNonNullElse(enabled, true);
        this.formTimeout = Objects.requireNonNullElse(formTimeout, DEFAULT_FORM_TIMEOUT);
        this.maxFormsPerNode = Math.max(0, Objects.requireNonNullElse(maxFormsPerNode, DEFAULT_MAX_FORMS_PER_NODE));
        this.approvalTimeout = Objects.requireNonNullElse(approvalTimeout, DEFAULT_APPROVAL_TIMEOUT);
        this.keepAliveInterval = Objects.requireNonNullElse(keepAliveInterval, DEFAULT_KEEP_ALIVE_INTERVAL);
        this.sessionRetention = Objects.requireNonNullElse(sessionRetention, DEFAULT_SESSION_RETENTION);
    }

    /// @return True if the endpoint serves MCP clients. Each user also needs the Use MCP Tools permission.
    @JsonPropertyDescription("Whether Stroom's MCP endpoint (/mcp) serves MCP clients. Each user also needs the "
                             + "'Use MCP Tools' permission, and is offered only the tools their MCP tool policies "
                             + "allow.")
    @JsonProperty("enabled")
    public boolean isEnabled() {
        return enabled;
    }

    /// @return How long a form a tool asks an MCP client's user to fill in waits for an answer.
    @JsonPropertyDescription("How long a form that one of Stroom's tools asks an MCP client's user to fill in (MCP "
                             + "elicitation, e.g. to approve a change) waits for an answer before it is cancelled. "
                             + "The tool call, and its thread, wait for it.")
    @JsonProperty("formTimeout")
    public StroomDuration getFormTimeout() {
        return formTimeout;
    }

    /// @return The most forms that may wait for answers from MCP clients' users on a node at once.
    @JsonPropertyDescription("The most forms (MCP elicitation) that may wait for answers from MCP clients' users "
                             + "on a node at once, as each holds a thread while it waits. Separate from the chat's "
                             + "maxElicitationsPerNode.")
    @JsonProperty("maxFormsPerNode")
    public int getMaxFormsPerNode() {
        return maxFormsPerNode;
    }

    /// @return How long an approval id given to an MCP client that can't show forms may be used.
    @JsonPropertyDescription("How long an approval id may be used: one is given to an MCP client that can't show "
                             + "forms, for a call that changes something, for the client to confirm with its user "
                             + "and pass back.")
    @JsonProperty("approvalTimeout")
    public StroomDuration getApprovalTimeout() {
        return approvalTimeout;
    }

    /// @return How often an idle MCP session's open stream is sent a ping.
    @JsonPropertyDescription("How often an MCP session's open stream is sent a ping, so that proxies and load "
                             + "balancers don't close it while it is idle. Keep it under their idle timeouts.")
    @JsonProperty("keepAliveInterval")
    public StroomDuration getKeepAliveInterval() {
        return keepAliveInterval;
    }

    /// @return How long the workspace of an MCP session that has had no requests is kept.
    @JsonPropertyDescription("How long the workspace of an MCP session that has had no requests is kept, for a "
                             + "session that ended without the client saying so (e.g. on a node that stopped). A "
                             + "session that ends normally has its workspace deleted at once.")
    @JsonProperty("sessionRetention")
    public StroomDuration getSessionRetention() {
        return sessionRetention;
    }

    @Override
    public String toString() {
        return "McpEndpointConfig{" +
               "enabled=" + enabled +
               ", formTimeout=" + formTimeout +
               ", maxFormsPerNode=" + maxFormsPerNode +
               ", approvalTimeout=" + approvalTimeout +
               ", keepAliveInterval=" + keepAliveInterval +
               ", sessionRetention=" + sessionRetention +
               '}';
    }
}
