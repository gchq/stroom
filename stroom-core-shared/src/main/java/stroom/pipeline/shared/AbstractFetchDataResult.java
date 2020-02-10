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
public abstract class AbstractFetchDataResult {
    private String streamType;
    private String classification;
    private OffsetRange<Long> streamRange;
    private RowCount<Long> streamRowCount;
    private OffsetRange<Long> pageRange;
    private RowCount<Long> pageRowCount;
    private List<String> availableChildStreamTypes;

    public AbstractFetchDataResult() {
        // Default constructor necessary for GWT serialisation.
    }

    public AbstractFetchDataResult(final String streamType, final String classification,
                                   final OffsetRange<Long> streamRange, final RowCount<Long> streamRowCount, final OffsetRange<Long> pageRange,
                                   final RowCount<Long> pageRowCount, final List<String> availableChildStreamTypes) {
        this.streamType = streamType;
        this.classification = classification;
        this.streamRange = streamRange;
        this.streamRowCount = streamRowCount;
        this.pageRange = pageRange;
        this.pageRowCount = pageRowCount;
        this.availableChildStreamTypes = availableChildStreamTypes;
    }

    public String getStreamType() {
        return streamType;
    }

    public void setStreamType(final String streamType) {
        this.streamType = streamType;
    }

    public String getClassification() {
        return classification;
    }

    public void setClassification(final String classification) {
        this.classification = classification;
    }

    public OffsetRange<Long> getStreamRange() {
        return streamRange;
    }

    public void setStreamRange(final OffsetRange<Long> streamRange) {
        this.streamRange = streamRange;
    }

    public RowCount<Long> getStreamRowCount() {
        return streamRowCount;
    }

    public void setStreamRowCount(final RowCount<Long> streamRowCount) {
        this.streamRowCount = streamRowCount;
    }

    public OffsetRange<Long> getPageRange() {
        return pageRange;
    }

    public void setPageRange(final OffsetRange<Long> pageRange) {
        this.pageRange = pageRange;
    }

    public RowCount<Long> getPageRowCount() {
        return pageRowCount;
    }

    public void setPageRowCount(final RowCount<Long> pageRowCount) {
        this.pageRowCount = pageRowCount;
    }

    public List<String> getAvailableChildStreamTypes() {
        return availableChildStreamTypes;
    }

    public void setAvailableChildStreamTypes(final List<String> availableChildStreamTypes) {
        this.availableChildStreamTypes = availableChildStreamTypes;
    }
}
