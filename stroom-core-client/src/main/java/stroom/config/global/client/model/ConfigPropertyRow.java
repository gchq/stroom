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

package stroom.config.global.client.model;

import stroom.config.global.shared.ConfigProperty;

/// A row of the global properties list ([ManageGlobalPropertyList]): a property, with its value and
/// source across the cluster.
public class ConfigPropertyRow {

    private final ConfigProperty configProperty;
    private final String effectiveValue;
    private final String source;

    /// @param configProperty The property.
    /// @param effectiveValue What to show as its value across the cluster, or `null` for its own.
    /// @param source         What to show as its source across the cluster, or `null` for its own.
    public ConfigPropertyRow(final ConfigProperty configProperty,
                             final String effectiveValue,
                             final String source) {
        this.configProperty = configProperty;
        this.effectiveValue = effectiveValue;
        this.source = source;
    }

    /// @param configProperty The property, as one node has it.
    public ConfigPropertyRow(final ConfigProperty configProperty) {
        this.configProperty = configProperty;
        this.effectiveValue = null;
        this.source = null;
    }

    /// @return The property.
    public ConfigProperty getConfigProperty() {
        return configProperty;
    }

    /// @return Its value, masked if secret.
    public String getEffectiveValueAsString() {
        return effectiveValue != null
                ? effectiveValue
                : configProperty.getEffectiveValueMasked().orElse(null);
    }

    /// @return Where its value comes from.
    public String getSourceAsString() {
        return source != null
                ? source
                : configProperty.getSource().getName();
    }

    /// @return Its name.
    public String getNameAsString() {
        return configProperty.getNameAsString();
    }

    /// @return Its description.
    public String getDescription() {
        return configProperty.getDescription();
    }
}
