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

package stroom.pipeline.shared;

import stroom.pipeline.shared.FetchDataRequest.DisplayMode;
import stroom.util.shared.Count;
import stroom.util.shared.Marker;
import stroom.util.shared.OffsetRange;
import stroom.util.shared.SerialisationTestConstructor;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Set;

@JsonInclude(JsonInclude.Include.NON_NULL)
public final class FetchMarkerResult extends AbstractFetchDataResult {

    public static final int MAX_MARKERS = 100;
    public static final int MAX_TOTAL_MARKERS = 1000;

    @JsonProperty
    private final List<Marker> markers;

    @JsonCreator
    public FetchMarkerResult(@JsonProperty("feedName") final String feedName,
                             @JsonProperty("streamTypeName") final String streamTypeName,
                             @JsonProperty("classification") final String classification,
                             @JsonProperty("sourceLocation") final SourceLocation sourceLocation,
                             @JsonProperty("itemRange") final OffsetRange itemRange,
                             @JsonProperty("totalItemCount") final Count<Long> totalItemCount,
                             @JsonProperty("totalCharacterCount") final Count<Long> totalCharacterCount,
                             @JsonProperty("availableChildStreamTypes") final Set<String> availableChildStreamTypes,
                             @JsonProperty("markers") final List<Marker> markers,
                             @JsonProperty("displayMode") final DisplayMode displayMode,
                             @JsonProperty("errors") final List<String> errors) {
        super(feedName,
                streamTypeName,
                classification,
                sourceLocation,
                itemRange,
                totalItemCount,
                totalCharacterCount,
                availableChildStreamTypes,
                displayMode,
                errors);

        if (!DisplayMode.MARKER.equals(displayMode)) {
            throw new IllegalArgumentException(
                    "Invalid displayMode " + displayMode + ". " +
                    "FetchMarkerResult should only ever have a display mode of MARKER");
        }

        this.markers = markers;
    }

    @SerialisationTestConstructor
    private FetchMarkerResult() {
        this(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                DisplayMode.MARKER,
                null);
    }

    public List<Marker> getMarkers() {
        return markers;
    }
}
