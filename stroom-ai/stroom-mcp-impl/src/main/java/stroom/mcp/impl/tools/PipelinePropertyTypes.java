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

import stroom.pipeline.factory.ElementRegistry;
import stroom.pipeline.factory.ElementRegistryFactory;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.shared.data.PipelinePropertyType;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.LinkedHashMap;
import java.util.Map;

/// The type Stroom declares for each pipeline element type's properties, as the pipeline editor reads them (the
/// Stroom MCP server's `property_types`, from `/pipeline/v1/propertyTypes`): `boolean`, `int`, `long`, `String`,
/// or the type of document the property names.
class PipelinePropertyTypes {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(PipelinePropertyTypes.class);

    private final Provider<ElementRegistryFactory> elementRegistryFactoryProvider;

    /// @param elementRegistryFactoryProvider Gives Stroom's pipeline element types.
    @Inject
    PipelinePropertyTypes(final Provider<ElementRegistryFactory> elementRegistryFactoryProvider) {
        this.elementRegistryFactoryProvider = elementRegistryFactoryProvider;
    }

    /// @return Each element type's properties and the type of each, by element type and property name; empty when
    /// Stroom gives none.
    Map<String, Map<String, String>> declared() {
        final ElementRegistry registry;
        try {
            registry = elementRegistryFactoryProvider.get().get();
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> "Unable to read the pipeline element types", e);
            return Map.of();
        }
        final Map<String, Map<String, String>> declared = new LinkedHashMap<>();
        NullSafe.map(NullSafe.get(registry, ElementRegistry::getPropertyTypes)).forEach((type, properties) -> {
            final Map<String, String> byName = new LinkedHashMap<>();
            NullSafe.map(properties).forEach((name, property) ->
                    byName.put(name, NullSafe.get(property, PipelinePropertyType::getType)));
            declared.put(NullSafe.get(type, PipelineElementType::getType), byName);
        });
        return declared;
    }
}
