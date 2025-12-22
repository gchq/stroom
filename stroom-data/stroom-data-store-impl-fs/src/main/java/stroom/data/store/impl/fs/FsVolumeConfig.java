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

package stroom.data.store.impl.fs;

import stroom.data.shared.StreamTypeNames;
import stroom.util.cache.CacheConfig;
import stroom.util.config.annotations.RequiresRestart;
import stroom.util.io.capacity.HasCapacitySelectorFactory;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.validation.constraints.Pattern;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

@JsonPropertyOrder(alphabetic = true)
public class FsVolumeConfig extends AbstractConfig implements IsStroomConfig {

    public static final String PROP_NAME_DEFAULT_VOLUME_GROUP_NAME = "defaultStreamVolumeGroupName";

    // TreeMap for consistent ordering in the yaml
    private static final Map<String, String> DEFAULT_META_TYPE_EXTENSIONS = new TreeMap<>(Map.of(
            StreamTypeNames.RAW_EVENTS, "revt",
            StreamTypeNames.RAW_REFERENCE, "rref",
            StreamTypeNames.EVENTS, "evt",
            StreamTypeNames.ERROR, "err",
            StreamTypeNames.REFERENCE, "ref",
            StreamTypeNames.TEST_EVENTS, "tevt",
            StreamTypeNames.TEST_REFERENCE, "tref",
            StreamTypeNames.DETECTIONS, "dtxn",
            StreamTypeNames.RECORDS, "rec"));

    private final String volumeSelector;

    // TODO 02/12/2021 AT: Make final
    private List<String> defaultStreamVolumePaths;
    private final double defaultStreamVolumeFilesystemUtilisation;
    private final boolean createDefaultStreamVolumesOnStart;
    private String defaultStreamVolumeGroupName;
    private final int findOrphanedMetaBatchSize;

    private final CacheConfig feedPathCache;
    private final CacheConfig typePathCache;
    // stream type name => legacy extension
    // e.g. 'Transient Raw' => '.trevt'
    private final Map<String, String> metaTypeExtensions;
    //    private final Map<String, String> metaTypeExtensionsReverseMap;
    private final StroomDuration maxVolumeStateAge;
    private final CacheConfig volumeCache;

    public FsVolumeConfig() {
        volumeSelector = "RoundRobin";
        defaultStreamVolumeGroupName = "Default Volume Group";
        defaultStreamVolumePaths = List.of("volumes/default_stream_volume");
        defaultStreamVolumeFilesystemUtilisation = 0.9;
        createDefaultStreamVolumesOnStart = true;
        findOrphanedMetaBatchSize = 7_000;

        feedPathCache = CacheConfig.builder()
                .maximumSize(1000L)
                .expireAfterAccess(StroomDuration.ofMinutes(10))
                .build();

        typePathCache = CacheConfig.builder()
                .maximumSize(1000L)
                .expireAfterAccess(StroomDuration.ofMinutes(10))
                .build();
        metaTypeExtensions = DEFAULT_META_TYPE_EXTENSIONS;
        // 30s should be enough time for all nodes to check the state after one node has updated it.
        maxVolumeStateAge = StroomDuration.ofSeconds(30);

        volumeCache = CacheConfig.builder()
                .maximumSize(1000L)
                .expireAfterAccess(StroomDuration.ofMinutes(10))
                .build();
    }

    @JsonCreator
    @SuppressWarnings("checkstyle:linelength")
    public FsVolumeConfig(
            @JsonProperty("volumeSelector") final String volumeSelector,
            @JsonProperty("defaultStreamVolumePaths") final List<String> defaultStreamVolumePaths,
            @JsonProperty("defaultStreamVolumeFilesystemUtilisation") final double defaultStreamVolumeFilesystemUtilisation,
            @JsonProperty("createDefaultStreamVolumesOnStart") final boolean createDefaultStreamVolumesOnStart,
            @JsonProperty(PROP_NAME_DEFAULT_VOLUME_GROUP_NAME) final String defaultStreamVolumeGroupName,
            @JsonProperty("feedPathCache") final CacheConfig feedPathCache,
            @JsonProperty("typePathCache") final CacheConfig typePathCache,
            @JsonProperty("metaTypeExtensions") final Map<String, String> metaTypeExtensions,
            @JsonProperty("findOrphanedMetaBatchSize") final int findOrphanedMetaBatchSize,
            @JsonProperty("maxVolumeStateAge") final StroomDuration maxVolumeStateAge,
            @JsonProperty("volumeCache") final CacheConfig volumeCache) {

        this.volumeSelector = volumeSelector;
        this.defaultStreamVolumePaths = defaultStreamVolumePaths;
        this.defaultStreamVolumeFilesystemUtilisation = defaultStreamVolumeFilesystemUtilisation;
        this.createDefaultStreamVolumesOnStart = createDefaultStreamVolumesOnStart;
        this.defaultStreamVolumeGroupName = defaultStreamVolumeGroupName;
        this.feedPathCache = feedPathCache;
        this.typePathCache = typePathCache;
        this.metaTypeExtensions = metaTypeExtensions;
        this.findOrphanedMetaBatchSize = findOrphanedMetaBatchSize;
        this.maxVolumeStateAge = maxVolumeStateAge;
        this.volumeCache = volumeCache;
    }

    @JsonPropertyDescription("How should volumes be selected for use? Possible volume selectors " +
            "include ('MostFreePercent', 'MostFree', 'Random', 'RoundRobinIgnoreLeastFreePercent', " +
            "'RoundRobinIgnoreLeastFree', 'RoundRobin', 'WeightedFreePercentRandom', 'WeightedFreeRandom') " +
            "default is 'RoundRobin'")
    @Pattern(regexp = HasCapacitySelectorFactory.VOLUME_SELECTOR_PATTERN)
    public String getVolumeSelector() {
        return volumeSelector;
    }

    @RequiresRestart(RequiresRestart.RestartScope.UI)
    @JsonPropertyDescription("If no existing stream volumes are present default volumes will be created on " +
            "application start.  Use property defaultStreamVolumePaths to define the volumes created.")
    public boolean isCreateDefaultStreamVolumesOnStart() {
        return createDefaultStreamVolumesOnStart;
    }

    @JsonPropertyDescription("The name of the default stream volume group that is created if none exist on " +
            "application start.")
    public String getDefaultStreamVolumeGroupName() {
        return defaultStreamVolumeGroupName;
    }

    public void setDefaultStreamVolumeGroupName(final String defaultStreamVolumeGroupName) {
        this.defaultStreamVolumeGroupName = defaultStreamVolumeGroupName;
    }

    public CacheConfig getFeedPathCache() {
        return feedPathCache;
    }

    public CacheConfig getTypePathCache() {
        return typePathCache;
    }

    @JsonPropertyDescription("The paths used if the default stream volumes are created on application start." +
            "If a path is a relative path then it will be treated as being relative to stroom.path.home.")
    public List<String> getDefaultStreamVolumePaths() {
        return defaultStreamVolumePaths;
    }

    @Deprecated(forRemoval = true)
    public void setDefaultStreamVolumePaths(final List<String> defaultStreamVolumePaths) {
        this.defaultStreamVolumePaths = defaultStreamVolumePaths;
    }

    @JsonPropertyDescription("Fraction of the filesystem beyond which the system will stop writing to the " +
            "default stream volumes that may be created on application start.")
    public double getDefaultStreamVolumeFilesystemUtilisation() {
        return defaultStreamVolumeFilesystemUtilisation;
    }

    @JsonPropertyDescription("Number of meta records to check in each batch.")
    public int getFindOrphanedMetaBatchSize() {
        return findOrphanedMetaBatchSize;
    }

    public FsVolumeConfig withDefaultStreamVolumePaths(final List<String> defaultStreamVolumePaths) {
        return new FsVolumeConfig(
                volumeSelector,
                defaultStreamVolumePaths,
                defaultStreamVolumeFilesystemUtilisation,
                createDefaultStreamVolumesOnStart,
                defaultStreamVolumeGroupName,
                feedPathCache,
                typePathCache,
                metaTypeExtensions,
                findOrphanedMetaBatchSize,
                maxVolumeStateAge,
                volumeCache);
    }

    public FsVolumeConfig withVolumeSelector(final String volumeSelector) {
        return new FsVolumeConfig(
                volumeSelector,
                defaultStreamVolumePaths,
                defaultStreamVolumeFilesystemUtilisation,
                createDefaultStreamVolumesOnStart,
                defaultStreamVolumeGroupName,
                feedPathCache,
                typePathCache,
                metaTypeExtensions,
                findOrphanedMetaBatchSize,
                maxVolumeStateAge,
                volumeCache);
    }

    @JsonPropertyDescription("Map of meta type names to their file extension. " +
            "You should only change this property if you need to support legacy file extensions used " +
            "before Stroom v7. If a meta type does not have an entry in this map then the extension " +
            "'dat' will be used. The extension is entered without the leading dot. Changing the extension for a " +
            "meta type would require manual renaming of existing files on the file system. Only do " +
            "this if you understand the consequences.")
    public Map<String, String> getMetaTypeExtensions() {
        return metaTypeExtensions;
    }

    @JsonIgnore
    public Optional<String> getMetaTypeExtension(final String metaTypeName) {
        if (metaTypeExtensions == null
                || metaTypeName == null
                || metaTypeName.isBlank()) {
            return Optional.empty();
        } else {
            return Optional.ofNullable(metaTypeExtensions.get(metaTypeName));
        }
    }

    @JsonPropertyDescription("When refreshing the local cache of volumes, the state will only be updated in the " +
            "database if it is older then this threshold age. Value must be less than the period of the job " +
            "'File System Volume Status'.")
    public StroomDuration getMaxVolumeStateAge() {
        return maxVolumeStateAge;
    }

    public CacheConfig getVolumeCache() {
        return volumeCache;
    }

    @Override
    public String toString() {
        return "FsVolumeConfig{" +
                "volumeSelector='" + volumeSelector + '\'' +
                ", defaultStreamVolumePaths=" + defaultStreamVolumePaths +
                ", defaultStreamVolumeFilesystemUtilisation=" + defaultStreamVolumeFilesystemUtilisation +
                ", createDefaultStreamVolumesOnStart=" + createDefaultStreamVolumesOnStart +
                ", defaultStreamVolumeGroupName=" + "\"" + defaultStreamVolumeGroupName + "\"" +
                ", findOrphanedMetaBatchSize=" + findOrphanedMetaBatchSize +
                ", feedPathCache=" + feedPathCache +
                ", typePathCache=" + typePathCache +
                ", metaTypeExtensions=" + metaTypeExtensions +
                ", maxVolumeStateAge=" + maxVolumeStateAge +
                ", volumeCache=" + volumeCache +
                '}';
    }
}
