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

import stroom.util.guice.ServletBinder;

import com.google.inject.AbstractModule;

/// Stroom's MCP endpoint, `/mcp`, and the metadata that tells MCP clients where to sign in to it.
public class McpModule extends AbstractModule {

    @Override
    protected void configure() {
        ServletBinder.create(binder())
                .bind(McpServlet.class)
                .bind(McpProtectedResourceServlet.class);
    }
}
