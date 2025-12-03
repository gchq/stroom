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
import stroom.util.shared.OffsetRange;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.List;
import java.util.Set;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = FetchDataResult.class, name = "data"),
        @JsonSubTypes.Type(value = FetchMarkerResult.class, name = "marker")
})
@JsonInclude(Include.NON_NULL)
public abstract sealed class AbstractFetchDataResult permits FetchDataResult, FetchMarkerResult {

    @JsonProperty
    private final String feedName;
    @JsonProperty
    private final String streamTypeName;
    @JsonProperty
    private final String classification;
    @JsonProperty
    private final SourceLocation sourceLocation;
    @JsonProperty
    private final OffsetRange itemRange; // part/segment/marker
    @JsonProperty
    private final Count<Long> totalItemCount; // part/segment/marker
    @JsonProperty
    private final Count<Long> totalCharacterCount; // Total chars in part/segment
    @JsonProperty
    private final Set<String> availableChildStreamTypes;
    @JsonProperty
    private final DisplayMode displayMode;
    @JsonProperty
    private final List<String> errors;


    @JsonCreator
    public AbstractFetchDataResult(
            @JsonProperty("feedName") final String feedName,
            @JsonProperty("streamTypeName") final String streamTypeName,
            @JsonProperty("classification") final String classification,
            @JsonProperty("sourceLocation") final SourceLocation sourceLocation,
            @JsonProperty("itemRange") final OffsetRange itemRange,
            @JsonProperty("totalItemCount") final Count<Long> totalItemCount,
            @JsonProperty("totalCharacterCount") final Count<Long> totalCharacterCount,
            @JsonProperty("availableChildStreamTypes") final Set<String> availableChildStreamTypes,
            @JsonProperty("displayMode") final DisplayMode displayMode,
            @JsonProperty("errors") final List<String> errors) {

        this.feedName = feedName;
        this.streamTypeName = streamTypeName;
        this.classification = classification;
        this.sourceLocation = sourceLocation;
        this.itemRange = itemRange;
        this.totalItemCount = totalItemCount;
        this.totalCharacterCount = totalCharacterCount;
        this.availableChildStreamTypes = availableChildStreamTypes;
        this.displayMode = displayMode;
        this.errors = errors;
    }

    public String getFeedName() {
        return feedName;
    }

    public String getStreamTypeName() {
        return streamTypeName;
    }

    public String getClassification() {
        return classification;
    }

    public SourceLocation getSourceLocation() {
        return sourceLocation;
    }

    public OffsetRange getItemRange() {
        return itemRange;
    }

    public Count<Long> getTotalItemCount() {
        return totalItemCount;
    }

    public Count<Long> getTotalCharacterCount() {
        return totalCharacterCount;
    }

    public Set<String> getAvailableChildStreamTypes() {
        return availableChildStreamTypes;
    }

    public DisplayMode getDisplayMode() {
        return displayMode;
    }

    public List<String> getErrors() {
        return errors;
    }

    public boolean hasErrors() {
        return errors != null && !errors.isEmpty();
    }
}
