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
import com.fasterxml.jackson.annotation.JsonProperty;
import stroom.util.shared.Marker;
import stroom.util.shared.OffsetRange;
import stroom.util.shared.RowCount;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_DEFAULT)
public class FetchMarkerResult extends AbstractFetchDataResult {
    public static final int MAX_MARKERS = 100;
    public static final int MAX_TOTAL_MARKERS = 1000;

    @JsonProperty
    private final List<Marker> markers;

    @JsonCreator
    public FetchMarkerResult(@JsonProperty("streamTypeName") final String streamTypeName,
                             @JsonProperty("classification") final String classification,
                             @JsonProperty("streamRange") final OffsetRange<Long> streamRange,
                             @JsonProperty("streamRowCount") final RowCount<Long> streamRowCount,
                             @JsonProperty("pageRange") final OffsetRange<Long> pageRange,
                             @JsonProperty("pageRowCount") final RowCount<Long> pageRowCount,
                             @JsonProperty("availableChildStreamTypes") final List<String> availableChildStreamTypes,
                             @JsonProperty("markers") final List<Marker> markers) {
        super(streamTypeName, classification, streamRange, streamRowCount, pageRange, pageRowCount, availableChildStreamTypes);
        this.markers = markers;
    }

    public List<Marker> getMarkers() {
        return markers;
    }
}
