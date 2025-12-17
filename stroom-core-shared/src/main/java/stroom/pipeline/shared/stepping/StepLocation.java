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

package stroom.pipeline.shared.stepping;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class StepLocation {

    // It seems javascript can't handle longs above 2^53 so use a max val
    // that is as big as it can handle.
    private static final long MAX_PART_INDEX = (long) (Math.pow(2, 53) - 1);
    private static final long MAX_RECORD_INDEX = MAX_PART_INDEX;

    @JsonProperty
    private final long metaId;

    // Zero based
    @JsonProperty
    private final long partIndex;

    // Zero based
    @JsonProperty
    private final long recordIndex;

    /**
     * @param metaId      The meta ID
     * @param partIndex   Zero based
     * @param recordIndex Zero based
     */
    @JsonCreator
    public StepLocation(@JsonProperty("metaId") final long metaId,
                        @JsonProperty("partIndex") final long partIndex,
                        @JsonProperty("recordIndex") final long recordIndex) {
        this.metaId = metaId;
        this.partIndex = partIndex;
        this.recordIndex = recordIndex;
    }

    public static StepLocation first(final long metaId) {
        return first(metaId, 0);
    }

    public static StepLocation first(final long metaId, final long partIndex) {
        return new StepLocation(metaId, partIndex, -1);
    }

    public static StepLocation last(final long metaId) {
        return last(metaId, MAX_PART_INDEX);
    }

    public static StepLocation last(final long metaId, final long partIndex) {
        return new StepLocation(metaId, partIndex, MAX_RECORD_INDEX);
    }

    public long getMetaId() {
        return metaId;
    }

    /**
     * Zero based
     */
    public long getPartIndex() {
        return partIndex;
    }

    /**
     * Zero based
     */
    public long getRecordIndex() {
        return recordIndex;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final StepLocation that = (StepLocation) o;
        return metaId == that.metaId &&
                partIndex == that.partIndex &&
                recordIndex == that.recordIndex;
    }

    @Override
    public int hashCode() {
        return Objects.hash(metaId, partIndex, recordIndex);
    }

    @JsonIgnore
    public String getEventId() {
        return metaId + ":" + (partIndex + 1) + ":" + (recordIndex + 1);
    }

    @Override
    public String toString() {
        return "[" + getEventId() + "]";
    }
}
