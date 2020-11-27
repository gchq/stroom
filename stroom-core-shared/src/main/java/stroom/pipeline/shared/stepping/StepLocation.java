/*
 * Copyright 2016 Crown Copyright
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
    private static final long MAX_PART_NO = (long) (Math.pow(2, 53) - 1);
    private static final long MAX_RECORD_NO = MAX_PART_NO;

    @JsonProperty
    private final long id;

    // One based
    @JsonProperty
    private final long partNo;

    // One based
    @JsonProperty
    private final long recordNo;

    @JsonCreator
    public StepLocation(@JsonProperty("id") final long id,
                        @JsonProperty("partNo") final long partNo,
                        @JsonProperty("recordNo") final long recordNo) {
        this.id = id;
        this.partNo = partNo;
        this.recordNo = recordNo;
    }

    public static StepLocation last(final long id) {
        return new StepLocation(id, MAX_PART_NO, MAX_RECORD_NO);
    }

    /**
     * @param partNo One based
     */
    public static StepLocation last(final long id, final long partNo) {
        return new StepLocation(id, partNo, MAX_RECORD_NO);
    }

    public long getId() {
        return id;
    }

    /**
     * One based
     */
    public long getPartNo() {
        return partNo;
    }

    /**
     * One based
     */
    public long getRecordNo() {
        return recordNo;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final StepLocation that = (StepLocation) o;
        return id == that.id &&
                partNo == that.partNo &&
                recordNo == that.recordNo;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, partNo, recordNo);
    }

    @JsonIgnore
    public String getEventId() {
        return id + ":" + partNo + ":" + recordNo;
    }

    @Override
    public String toString() {
        return "[" + getEventId() + "]";
    }
}
