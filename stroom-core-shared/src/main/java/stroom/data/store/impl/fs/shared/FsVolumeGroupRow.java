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

package stroom.data.store.impl.fs.shared;

import stroom.util.shared.NullSafe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class FsVolumeGroupRow {

    @JsonProperty
    private final FsVolumeGroup group;
    @JsonProperty
    private final int volumeCount;
    @JsonProperty
    private final List<FsVolumeType> volumeTypes;

    @JsonCreator
    public FsVolumeGroupRow(@JsonProperty("group") final FsVolumeGroup group,
                            @JsonProperty("volumeCount") final int volumeCount,
                            @JsonProperty("volumeTypes") final List<FsVolumeType> volumeTypes) {
        this.group = Objects.requireNonNull(group);
        this.volumeCount = volumeCount;
        this.volumeTypes = NullSafe.unmodifiableList(volumeTypes);
    }

    public FsVolumeGroup getGroup() {
        return group;
    }

    public int getVolumeCount() {
        return volumeCount;
    }

    public List<FsVolumeType> getVolumeTypes() {
        return volumeTypes;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final FsVolumeGroupRow that = (FsVolumeGroupRow) o;
        return volumeCount == that.volumeCount &&
               Objects.equals(group, that.group) &&
               Objects.equals(volumeTypes, that.volumeTypes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(group, volumeCount, volumeTypes);
    }

    @Override
    public String toString() {
        return "FsVolumeGroupRow{" +
               "group=" + group +
               ", volumeCount=" + volumeCount +
               ", volumeTypes=" + volumeTypes +
               '}';
    }
}
