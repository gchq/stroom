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

package stroom.importexport.impl;

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;


@JsonPropertyOrder(alphabetic = true)
public class ExportConfig extends AbstractConfig implements IsStroomConfig {

    private static final boolean DEFAULT_ENABLED = false;

    protected static final String ENABLED_PROP_NAME = "enabled";
    private final boolean enabled;

    public ExportConfig() {
        enabled = DEFAULT_ENABLED;
    }

    @JsonCreator
    public ExportConfig(@JsonProperty(ENABLED_PROP_NAME) final Boolean enabled) {
        this.enabled = Objects.requireNonNullElse(enabled, DEFAULT_ENABLED);
    }

    @JsonPropertyDescription("Determines if the system will allow configuration to be exported via the export servlet")
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public String toString() {
        return "ExportConfig{" +
                "enabled=" + enabled +
                '}';
    }
}
