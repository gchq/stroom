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

package stroom.pipeline.shared;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import stroom.util.shared.OffsetRange;
import stroom.util.shared.RowCount;

import java.util.List;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = FetchDataResult.class, name = "data"),
        @JsonSubTypes.Type(value = FetchMarkerResult.class, name = "marker")
})
@JsonInclude(Include.NON_DEFAULT)
public abstract class AbstractFetchDataResult {
    @JsonProperty
    private final String streamTypeName;
    @JsonProperty
    private final String classification;
    @JsonProperty
    private final OffsetRange<Long> streamRange;
    @JsonProperty
    private final RowCount<Long> streamRowCount;
    @JsonProperty
    private final OffsetRange<Long> pageRange;
    @JsonProperty
    private final RowCount<Long> pageRowCount;
    @JsonProperty
    private final List<String> availableChildStreamTypes;

    @JsonCreator
    public AbstractFetchDataResult(@JsonProperty("streamTypeName") final String streamTypeName,
                                   @JsonProperty("classification") final String classification,
                                   @JsonProperty("streamRange") final OffsetRange<Long> streamRange,
                                   @JsonProperty("streamRowCount") final RowCount<Long> streamRowCount,
                                   @JsonProperty("pageRange") final OffsetRange<Long> pageRange,
                                   @JsonProperty("pageRowCount") final RowCount<Long> pageRowCount,
                                   @JsonProperty("availableChildStreamTypes") final List<String> availableChildStreamTypes) {
        this.streamTypeName = streamTypeName;
        this.classification = classification;
        this.streamRange = streamRange;
        this.streamRowCount = streamRowCount;
        this.pageRange = pageRange;
        this.pageRowCount = pageRowCount;
        this.availableChildStreamTypes = availableChildStreamTypes;
    }

    public String getStreamTypeName() {
        return streamTypeName;
    }

    public String getClassification() {
        return classification;
    }

    public OffsetRange<Long> getStreamRange() {
        return streamRange;
    }

    public RowCount<Long> getStreamRowCount() {
        return streamRowCount;
    }

    public OffsetRange<Long> getPageRange() {
        return pageRange;
    }

    public RowCount<Long> getPageRowCount() {
        return pageRowCount;
    }

    public List<String> getAvailableChildStreamTypes() {
        return availableChildStreamTypes;
    }
}
