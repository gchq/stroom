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

import stroom.ai.impl.AiConfig;
import stroom.ai.impl.AiSampleUploadsConfig;
import stroom.ai.impl.McpEndpointConfig;
import stroom.util.config.AbstractConfigUtil;
import stroom.util.shared.PropertyPath;

import java.util.Map;
import java.util.Objects;

/// Changes to the AI config for tests, to give a test's config mapper
/// ([stroom.test.ConfigMapperSpy#setConfigValueMapper]): the config tree rebuilt as Stroom rebuilds it when a
/// property changes ([AbstractConfigUtil#mutateTree]).
final class AiConfigChanges {

    // Where the AI config lives in the tree, should it not say.
    private static final PropertyPath DEFAULT_PATH = PropertyPath.fromParts("stroom", "ai");

    private AiConfigChanges() {
    }

    /// @param config   The AI config.
    /// @param endpoint The MCP endpoint's config to use.
    /// @return The AI config with that MCP endpoint config.
    static AiConfig withMcpEndpoint(final AiConfig config, final McpEndpointConfig endpoint) {
        return replace(config, "mcpEndpoint", endpoint);
    }

    /// @param config        The AI config.
    /// @param sampleUploads The sample uploads' config to use.
    /// @return The AI config with that sample uploads config.
    static AiConfig withSampleUploads(final AiConfig config, final AiSampleUploadsConfig sampleUploads) {
        return replace(config, "sampleUploads", sampleUploads);
    }

    private static AiConfig replace(final AiConfig config, final String property, final Object value) {
        final PropertyPath path = Objects.requireNonNullElse(config.getBasePath(), DEFAULT_PATH);
        return AbstractConfigUtil.mutateTree(config, path, Map.of(path.merge(property), value));
    }
}
