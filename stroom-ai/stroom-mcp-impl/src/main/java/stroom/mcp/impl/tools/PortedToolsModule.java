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

package stroom.mcp.impl.tools;

import stroom.ai.impl.tools.StroomTool;
import stroom.util.guice.GuiceUtil;

import com.google.inject.AbstractModule;

/// Binds the tools that came from the Stroom MCP server, alongside the built-in ones (see
/// [stroom.ai.impl.tools.BuiltInToolsModule]): they are offered in the chat and over `/mcp` alike. Add a new
/// [StroomTool] and a single `.addBinding(...)` line here.
public class PortedToolsModule extends AbstractModule {

    @Override
    protected void configure() {
        GuiceUtil.buildMultiBinder(binder(), StroomTool.class)
                .addBinding(FindPipelineTemplatesTool.class)
                .addBinding(DescribeTemplateTool.class)
                .addBinding(DescribeFeedTool.class)
                .addBinding(FindReferenceDataTool.class)
                .addBinding(FindElasticClustersTool.class)
                .addBinding(SummariseStreamsTool.class)
                .addBinding(ProcessingStatusTool.class)
                .addBinding(LocateEventTool.class);
    }
}
