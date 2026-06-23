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

package stroom.explorer.impl;

import stroom.config.common.AbstractDbConfig;
import stroom.config.common.ConnectionConfig;
import stroom.config.common.ConnectionPoolConfig;
import stroom.config.common.HasDbConfig;
import stroom.explorer.shared.ExplorerNode;
import stroom.explorer.shared.StandardExplorerTags;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.BootStrapConfig;
import stroom.util.shared.IsStroomConfig;
import stroom.util.shared.NullSafe;
import stroom.util.shared.validation.AllMatchPattern;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Arrays;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;


@JsonPropertyOrder(alphabetic = true)
public class ExplorerConfig extends AbstractConfig implements IsStroomConfig, HasDbConfig {

    private static final boolean DEFAULT_DEPENDENCY_WARNINGS_ENABLED = false;

    private final ExplorerDbConfig dbConfig;
    private final Set<String> suggestedTags;
    private final boolean dependencyWarningsEnabled;

    public ExplorerConfig() {
        dbConfig = new ExplorerDbConfig();
        suggestedTags = Arrays.stream(StandardExplorerTags.values())
                .map(StandardExplorerTags::getTagName)
                .collect(Collectors.toSet());
        dependencyWarningsEnabled = DEFAULT_DEPENDENCY_WARNINGS_ENABLED;
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public ExplorerConfig(@JsonProperty("db") final ExplorerDbConfig dbConfig,
                          @JsonProperty("suggestedTags") final Set<String> suggestedTags,
                          @JsonProperty("dependencyWarningsEnabled") final Boolean dependencyWarningsEnabled) {
        this.dbConfig = dbConfig;
        // Filter out any blanks
        this.suggestedTags = NullSafe.stream(suggestedTags)
                .filter(tag -> !NullSafe.isBlankString(tag))
                .collect(Collectors.toSet());
        this.dependencyWarningsEnabled =
                Objects.requireNonNullElse(dependencyWarningsEnabled, DEFAULT_DEPENDENCY_WARNINGS_ENABLED);
    }

    @Override
    @JsonProperty("db")
    public ExplorerDbConfig getDbConfig() {
        return dbConfig;
    }

    @AllMatchPattern(pattern = ExplorerNode.TAG_PATTERN_STR)
    @JsonPropertyDescription(
            "A set of explorer node tags that will be provided to the user to pick from " +
            "along with any custom tags added to nodes by the user.")
    @JsonProperty("suggestedTags")
    public Set<String> getSuggestedTags() {
        return Objects.requireNonNullElseGet(suggestedTags, Collections::emptySet);
    }

    @JsonPropertyDescription(
            "Enables warning indicators in the explorer tree for documents with broken dependencies")
    @JsonProperty("dependencyWarningsEnabled")
    public boolean getDependencyWarningsEnabled() {
        return dependencyWarningsEnabled;
    }

    // --------------------------------------------------------------------------------


    @BootStrapConfig
    public static class ExplorerDbConfig extends AbstractDbConfig {

        public ExplorerDbConfig() {
            super();
        }

        @SuppressWarnings("unused")
        @JsonCreator
        public ExplorerDbConfig(
                @JsonProperty(PROP_NAME_CONNECTION) final ConnectionConfig connectionConfig,
                @JsonProperty(PROP_NAME_CONNECTION_POOL) final ConnectionPoolConfig connectionPoolConfig) {
            super(connectionConfig, connectionPoolConfig);
        }
    }
}
