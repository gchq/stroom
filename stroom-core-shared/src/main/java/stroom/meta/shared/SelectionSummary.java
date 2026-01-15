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

package stroom.meta.shared;

import stroom.util.shared.NullSafe;
import stroom.util.shared.Range;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Set;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class SelectionSummary {

    // MySQL by defaults truncates the group_concat value to 1024 so the number of distinct values
    // needs to fit in 1024chars when delimited
    public static final int MAX_GROUP_CONCAT_PARTS = 20;

    @JsonProperty
    private final long itemCount;
    @JsonProperty
    private final long feedCount;
    @JsonProperty
    private final Set<String> distinctFeeds;
    @JsonProperty
    private final long typeCount;
    @JsonProperty
    private final Set<String> distinctTypes;
    @JsonProperty
    private final long processorCount;
    @JsonProperty
    private final long pipelineCount;
    @JsonProperty
    private final long statusCount;
    @JsonProperty
    private final Set<String> distinctStatuses;
    @JsonProperty
    private final Range<Long> ageRange;

    @JsonCreator
    public SelectionSummary(@JsonProperty("itemCount") final long itemCount,
                            @JsonProperty("feedCount") final long feedCount,
                            @JsonProperty("distinctFeeds") final Set<String> distinctFeeds,
                            @JsonProperty("typeCount") final long typeCount,
                            @JsonProperty("distinctTypes") final Set<String> distinctTypes,
                            @JsonProperty("processorCount") final long processorCount,
                            @JsonProperty("pipelineCount") final long pipelineCount,
                            @JsonProperty("statusCount") final long statusCount,
                            @JsonProperty("distinctStatuses") final Set<String> distinctStatuses,
                            @JsonProperty("ageRange") final Range<Long> ageRange) {
        this.itemCount = itemCount;
        this.feedCount = feedCount;
        this.distinctFeeds = distinctFeeds;
        this.typeCount = typeCount;
        this.distinctTypes = distinctTypes;
        this.processorCount = processorCount;
        this.pipelineCount = pipelineCount;
        this.statusCount = statusCount;
        this.distinctStatuses = distinctStatuses;
        this.ageRange = ageRange;
    }

    public long getItemCount() {
        return itemCount;
    }

    public long getFeedCount() {
        return feedCount;
    }

    /**
     * @return Set of feed names affected. May have been truncated if too many.
     * compare {@link SelectionSummary#getFeedCount()} against the size of this set to
     * establish if truncation has happened.
     */
    public Set<String> getDistinctFeeds() {
        return NullSafe.set(distinctFeeds);
    }

    public long getTypeCount() {
        return typeCount;
    }

    /**
     * @return Set of stream types affected. May have been truncated if too many.
     * compare {@link SelectionSummary#getTypeCount()} against the size of this set to
     * establish if truncation has happened.
     */
    public Set<String> getDistinctTypes() {
        return NullSafe.set(distinctTypes);
    }

    public long getProcessorCount() {
        return processorCount;
    }

    public long getPipelineCount() {
        return pipelineCount;
    }

    public long getStatusCount() {
        return statusCount;
    }

    /**
     * @return Set of status affected. May have been truncated if too many.
     * compare {@link SelectionSummary#getStatusCount()} against the size of this set to
     * establish if truncation has happened.
     */
    public Set<String> getDistinctStatuses() {
        return distinctStatuses;
    }

    public Range<Long> getAgeRange() {
        return ageRange;
    }

    @Override
    public String toString() {
        return "SelectionSummary{" +
                "itemCount=" + itemCount +
                ", feedCount=" + feedCount +
                ", distinctFeeds=" + distinctFeeds +
                ", typeCount=" + typeCount +
                ", distinctTypes=" + distinctTypes +
                ", processorCount=" + processorCount +
                ", pipelineCount=" + pipelineCount +
                ", statusCount=" + statusCount +
                ", ageRange=" + ageRange +
                '}';
    }
}
