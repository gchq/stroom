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

package stroom.mcp.impl;

import stroom.ai.impl.tools.AgentKnowledge;
import stroom.job.api.ScheduledJobsBinder;
import stroom.mcp.impl.tools.PortedToolsModule;
import stroom.util.RunnableWrapper;
import stroom.util.guice.ServletBinder;

import com.google.inject.AbstractModule;
import jakarta.inject.Inject;

/// Stroom's MCP endpoint, `/mcp`, where sample files are posted (`/mcp/upload`), the metadata that tells MCP
/// clients where to sign in to it, and the knowledge bundled for agents.
public class McpModule extends AbstractModule {

    /// The job that ends idle MCP sessions, and those the transport has dropped.
    static final String SESSION_JOB_NAME = "MCP Session Maintenance";

    @Override
    protected void configure() {
        install(new PortedToolsModule());
        // The guides and conventions bundled for agents: MCP resources here, and get_instructions' guides in the
        // chat (stroom-ai-impl declares it optional).
        bind(AgentKnowledge.class).to(BundledKnowledge.class);

        ServletBinder.create(binder())
                .bind(McpServlet.class)
                .bind(McpUploadServlet.class)
                .bind(McpProtectedResourceServlet.class);

        // On every node, as each holds its own sessions; not inside users' requests.
        ScheduledJobsBinder.create(binder())
                .bindJobTo(McpSessionMaintenance.class, builder -> builder
                        .name(SESSION_JOB_NAME)
                        .description("Ends MCP sessions that have been idle for the session retention time, and "
                                     + "those whose clients have gone, deleting their workspaces")
                        .managed(false)
                        .frequencySchedule("1m"));
    }


    // --------------------------------------------------------------------------------


    private static class McpSessionMaintenance extends RunnableWrapper {

        @Inject
        McpSessionMaintenance(final StroomMcpServer server) {
            super(server::maintain);
        }
    }
}
