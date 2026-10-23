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

package stroom.planb.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

/**
 * What a pathways document has in common with every other store on the shared file store: where its
 * shards live and how many there are.
 *
 * <p>A pathways document keeps its own shared file store setting rather than one of these, and builds
 * this from it — so the document on disk is unchanged and this exists only to answer
 * {@link SharedFileStore}, which asks every document the same way.
 *
 * <p>Carries no retention of its own. A pathways model is held to its window by the narrowing, which
 * is a different thing on a different schedule, and retention left at its default is off.
 */
@JsonPropertyOrder({"maxStoreSize", "retention", "sharedFileStore"})
@JsonInclude(Include.NON_NULL)
public final class PathwaysSettings extends AbstractPlanBSettings implements HasSharedFileStore {

    @JsonProperty
    private final SharedFileStoreSettings sharedFileStore;

    @JsonCreator
    public PathwaysSettings(@JsonProperty("maxStoreSize") final Long maxStoreSize,
                            @JsonProperty("retention") final RetentionSettings retention,
                            @JsonProperty("sharedFileStore") final SharedFileStoreSettings sharedFileStore) {
        super(maxStoreSize, retention);
        this.sharedFileStore = sharedFileStore;
    }

    public PathwaysSettings(final SharedFileStoreSettings sharedFileStore) {
        this(null, null, sharedFileStore);
    }

    @Override
    public SharedFileStoreSettings getSharedFileStore() {
        return sharedFileStore;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final PathwaysSettings that = (PathwaysSettings) o;
        return Objects.equals(sharedFileStore, that.sharedFileStore);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sharedFileStore);
    }

    @Override
    public String toString() {
        return "PathwaysSettings{sharedFileStore=" + sharedFileStore + "}";
    }
}
