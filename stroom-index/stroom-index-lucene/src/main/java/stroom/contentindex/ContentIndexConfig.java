/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.contentindex;


import stroom.util.config.annotations.RequiresRestart;
import stroom.util.config.annotations.RequiresRestart.RestartScope;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;
import stroom.util.shared.NullSafe;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ContentIndexConfig extends AbstractConfig implements IsStroomConfig {

    private static final String DEFAULT_CONTENT_INDEX_DIR = "content_index";
    private static final StorageType DEFAULT_STORAGE_TYPE = StorageType.LOCAL;
    private static final StroomDuration DEFAULT_MIN_REBUILD_AGE = StroomDuration.ofMinutes(1);

    @JsonProperty
    private final String contentIndexDir;
    @JsonProperty
    private final StorageType storageType;
    @JsonProperty
    private final StroomDuration minRebuildAge;

    public ContentIndexConfig() {
        contentIndexDir = DEFAULT_CONTENT_INDEX_DIR;
        storageType = DEFAULT_STORAGE_TYPE;
        minRebuildAge = DEFAULT_MIN_REBUILD_AGE;
    }

    @JsonCreator
    public ContentIndexConfig(@JsonProperty("contentIndexDir") final String contentIndexDir,
                              @JsonProperty("storageType") final StorageType storageType,
                              @JsonProperty("minRebuildAge") final StroomDuration minRebuildAge) {
        this.contentIndexDir = NullSafe.nonBlankStringElse(contentIndexDir, DEFAULT_CONTENT_INDEX_DIR);
        this.storageType = Objects.requireNonNullElse(storageType, DEFAULT_STORAGE_TYPE);
        this.minRebuildAge = Objects.requireNonNullElse(minRebuildAge, DEFAULT_MIN_REBUILD_AGE);
    }

    @RequiresRestart(RestartScope.UI)
    @JsonPropertyDescription("The directory where the index of Stroom content will be stored. " +
                             "If relative, the path be relative to stroom.home (or stroom.temp if " +
                             "storageType is TEMP). If null 'content_index' will be used.")
    public String getContentIndexDir() {
        return contentIndexDir;
    }

    @RequiresRestart(RestartScope.UI)
    @JsonPropertyDescription("The type of storage that contentIndexDir sits on. " +
                             "'SHARED' - Storage that is shared between all nodes. " +
                             "'LOCAL' - Storage that is local to the node and only access by that node. " +
                             "'TEMP' - Ephemeral storage that is local to the node and may be lost on host reboot. " +
                             "If null a default of 'TEMP' is used. " +
                             "TEMP will be more performant, but will require an index re-build on host reboot.")
    public StorageType getStorageType() {
        return storageType;
    }

    @JsonPropertyDescription("The minimum age of the content index for a rebuild to happen.")
    public StroomDuration getMinRebuildAge() {
        return minRebuildAge;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ContentIndexConfig that = (ContentIndexConfig) o;
        return Objects.equals(contentIndexDir, that.contentIndexDir)
               && storageType == that.storageType
               && Objects.equals(minRebuildAge, that.minRebuildAge);
    }

    @Override
    public int hashCode() {
        return Objects.hash(contentIndexDir, storageType, minRebuildAge);
    }

    @Override
    public String toString() {
        return "ContentIndexConfig{" +
               "contentIndexDir='" + contentIndexDir + '\'' +
               ", storageType=" + storageType +
               ", minRebuildAge=" + minRebuildAge +
               '}';
    }


    // --------------------------------------------------------------------------------


    public enum StorageType {
        /**
         * On local node specific storage
         */
        LOCAL(false),
        /**
         * On storage shared by all nodes
         */
        SHARED(true),
        /**
         * On local node specific temporary storage. May be lost on host reboot.
         */
        TEMP(false),
        ;

        private final boolean isSharedStorage;

        StorageType(final boolean isSharedStorage) {
            this.isSharedStorage = isSharedStorage;
        }

        public boolean isSharedStorage() {
            return isSharedStorage;
        }
    }
}
