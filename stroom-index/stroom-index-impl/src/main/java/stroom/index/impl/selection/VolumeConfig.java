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

package stroom.index.impl.selection;

import stroom.util.cache.CacheConfig;
import stroom.util.config.annotations.RequiresRestart;
import stroom.util.io.capacity.HasCapacitySelectorFactory;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.validation.constraints.Pattern;

import java.util.List;

@JsonPropertyOrder(alphabetic = true)
public class VolumeConfig extends AbstractConfig implements IsStroomConfig {

    public static final String PROP_NAME_DEFAULT_VOLUME_GROUP_NAME = "defaultIndexVolumeGroupName";

    private final String volumeSelector;
    private final boolean createDefaultIndexVolumesOnStart;
    private final String defaultIndexVolumeGroupName;
    private final List<String> defaultIndexVolumeGroupPaths;
    private final double defaultIndexVolumeFilesystemUtilisation;
    private final CacheConfig volumeSelectorCache;

    public VolumeConfig() {
        volumeSelector = "RoundRobin";
        createDefaultIndexVolumesOnStart = true;
        defaultIndexVolumeGroupName = "Default Volume Group";
        defaultIndexVolumeGroupPaths = List.of("volumes/default_index_volume");
        defaultIndexVolumeFilesystemUtilisation = 0.9;
        // Most volume selectors hold state (e.g. round-robin position) so we need to cache
        // them. Use a fairly long life do avoid the selector being aged off and the position
        // being lost.
        volumeSelectorCache = CacheConfig.builder()
                .maximumSize(1_000L)
                .expireAfterAccess(StroomDuration.ofDays(10))
                .build();
    }

    @SuppressWarnings({"unused", "checkstyle:linelength"})
    @JsonCreator
    public VolumeConfig(
            @JsonProperty("volumeSelector") final String volumeSelector,
            @JsonProperty("createDefaultIndexVolumesOnStart") final boolean createDefaultIndexVolumesOnStart,
            @JsonProperty(PROP_NAME_DEFAULT_VOLUME_GROUP_NAME) final String defaultIndexVolumeGroupName,
            @JsonProperty("defaultIndexVolumeGroupPaths") final List<String> defaultIndexVolumeGroupPaths,
            @JsonProperty("defaultIndexVolumeFilesystemUtilisation") final double defaultIndexVolumeFilesystemUtilisation,
            @JsonProperty("volumeSelectorCache") final CacheConfig volumeSelectorCache) {
        this.volumeSelector = volumeSelector;
        this.createDefaultIndexVolumesOnStart = createDefaultIndexVolumesOnStart;
        this.defaultIndexVolumeGroupName = defaultIndexVolumeGroupName;
        this.defaultIndexVolumeGroupPaths = defaultIndexVolumeGroupPaths;
        this.defaultIndexVolumeFilesystemUtilisation = defaultIndexVolumeFilesystemUtilisation;
        this.volumeSelectorCache = volumeSelectorCache;
    }

    @JsonPropertyDescription("How should index volumes be selected for use? Possible volume selectors " +
            "include ('MostFreePercent', 'MostFree', 'Random', 'RoundRobinIgnoreLeastFreePercent', " +
            "'RoundRobinIgnoreLeastFree', 'RoundRobin', 'WeightedFreePercentRandom', 'WeightedFreeRandom') " +
            "default is 'RoundRobin'")
    @Pattern(regexp = HasCapacitySelectorFactory.VOLUME_SELECTOR_PATTERN)
    public String getVolumeSelector() {
        return volumeSelector;
    }

    @RequiresRestart(RequiresRestart.RestartScope.UI)
    @JsonPropertyDescription("If no existing index volume groups are present a default volume group will be " +
            "created on application start. Use property defaultIndexVolumeGroupName to define its name")
    public boolean isCreateDefaultIndexVolumesOnStart() {
        return createDefaultIndexVolumesOnStart;
    }

    @JsonPropertyDescription("The name of the default index volume group that is created if none exist on " +
            "application start. Use properties defaultIndexVolumeGroupLimit, defaultIndexVolumeGroupPaths " +
            "and defaultIndexVolumeGroupNodes to specify details.")
    public String getDefaultIndexVolumeGroupName() {
        return defaultIndexVolumeGroupName;
    }

    @JsonPropertyDescription("The paths on the nodes that hold the data and are created " +
            "on the defined list of nodes if the default index is created on application start. " +
            "N.B. It is possible to have multiple paths per node and/or the same path repeated on multiple " +
            "nodes but there must always be the same number of elements in this list as in property " +
            "defaultIndexVolumeGroupNodes. If a path is a relative path then it will be treated as being " +
            "relative to stroom.path.home.")
    public List<String> getDefaultIndexVolumeGroupPaths() {
        return defaultIndexVolumeGroupPaths;
    }

    @JsonPropertyDescription("Fraction of the filesystem beyond which the system will stop writing to the " +
            "default index volumes that may be created on application start.")
    public double getDefaultIndexVolumeFilesystemUtilisation() {
        return defaultIndexVolumeFilesystemUtilisation;
    }

    public CacheConfig getVolumeSelectorCache() {
        return volumeSelectorCache;
    }

    public VolumeConfig withVolumeSelector(final String volumeSelector) {
        return new VolumeConfig(
                volumeSelector,
                createDefaultIndexVolumesOnStart,
                defaultIndexVolumeGroupName,
                defaultIndexVolumeGroupPaths,
                defaultIndexVolumeFilesystemUtilisation,
                volumeSelectorCache);
    }

    @Override
    public String toString() {
        return "VolumeConfig{" +
                ", volumeSelector='" + volumeSelector + '\'' +
                ", createDefaultIndexVolumesOnStart=" + createDefaultIndexVolumesOnStart +
                ", defaultIndexVolumeGroupName=" + "\"" + defaultIndexVolumeGroupName + "\"" +
                ", defaultIndexVolumeFilesystemUtilisation=" + defaultIndexVolumeFilesystemUtilisation +
                ", defaultIndexVolumeGroupPaths=" + "\"" + defaultIndexVolumeGroupPaths + "\"" +
                '}';
    }
}
