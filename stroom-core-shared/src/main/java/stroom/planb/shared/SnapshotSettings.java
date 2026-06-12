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

package stroom.planb.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder({
        "useSnapshotsForLookup",
        "useSnapshotsForGet",
        "useSnapshotsForQuery"
})
@JsonInclude(Include.NON_NULL)
public class SnapshotSettings {

    private static final Boolean DEFAULT_USE_SNAPSHOTS_FOR_LOOKUP = false;
    private static final Boolean DEFAULT_USE_SNAPSHOTS_FOR_GET = false;
    private static final Boolean DEFAULT_USE_SNAPSHOTS_FOR_QUERY = false;

    @JsonProperty
    private final boolean useSnapshotsForLookup;
    @JsonProperty
    private final boolean useSnapshotsForGet;
    @JsonProperty
    private final boolean useSnapshotsForQuery;

    public SnapshotSettings() {
        this.useSnapshotsForLookup = DEFAULT_USE_SNAPSHOTS_FOR_LOOKUP;
        this.useSnapshotsForGet = DEFAULT_USE_SNAPSHOTS_FOR_GET;
        this.useSnapshotsForQuery = DEFAULT_USE_SNAPSHOTS_FOR_QUERY;
    }

    @JsonCreator
    public SnapshotSettings(@JsonProperty("useSnapshotsForLookup") final Boolean useSnapshotsForLookup,
                            @JsonProperty("useSnapshotsForGet") final Boolean useSnapshotsForGet,
                            @JsonProperty("useSnapshotsForQuery") final Boolean useSnapshotsForQuery) {
        this.useSnapshotsForLookup =
                Objects.requireNonNullElse(useSnapshotsForLookup, DEFAULT_USE_SNAPSHOTS_FOR_LOOKUP);
        this.useSnapshotsForGet =
                Objects.requireNonNullElse(useSnapshotsForGet, DEFAULT_USE_SNAPSHOTS_FOR_GET);
        this.useSnapshotsForQuery =
                Objects.requireNonNullElse(useSnapshotsForQuery, DEFAULT_USE_SNAPSHOTS_FOR_QUERY);
    }

    public boolean isUseSnapshotsForLookup() {
        return useSnapshotsForLookup;
    }

    public boolean isUseSnapshotsForGet() {
        return useSnapshotsForGet;
    }

    public boolean isUseSnapshotsForQuery() {
        return useSnapshotsForQuery;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final SnapshotSettings that = (SnapshotSettings) o;
        return useSnapshotsForLookup == that.useSnapshotsForLookup
               && useSnapshotsForGet == that.useSnapshotsForGet
               && useSnapshotsForQuery == that.useSnapshotsForQuery;
    }

    @Override
    public int hashCode() {
        return Objects.hash(useSnapshotsForLookup, useSnapshotsForGet, useSnapshotsForQuery);
    }

    @Override
    public String toString() {
        return "SnapshotSettings{" +
               "useSnapshotsForLookup=" + useSnapshotsForLookup +
               ", useSnapshotsForGet=" + useSnapshotsForGet +
               ", useSnapshotsForQuery=" + useSnapshotsForQuery +
               '}';
    }
}
