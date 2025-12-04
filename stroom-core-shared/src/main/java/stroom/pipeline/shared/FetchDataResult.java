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

import stroom.data.shared.DataType;
import stroom.pipeline.shared.FetchDataRequest.DisplayMode;
import stroom.util.shared.Count;
import stroom.util.shared.OffsetRange;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@JsonInclude(JsonInclude.Include.NON_NULL)
public final class FetchDataResult extends AbstractFetchDataResult {

    @JsonProperty
    private final String data;
    @JsonProperty
    private final boolean html;
    @JsonProperty
    private final DataType dataType;
    @JsonProperty
    private final Long totalBytes;

    @JsonCreator
    public FetchDataResult(@JsonProperty("feedName") final String feedName,
                           @JsonProperty("streamTypeName") final String streamTypeName,
                           @JsonProperty("classification") final String classification,
                           @JsonProperty("sourceLocation") final SourceLocation sourceLocation,
                           @JsonProperty("itemRange") final OffsetRange itemRange,
                           @JsonProperty("totalItemCount") final Count<Long> totalItemCount,
                           @JsonProperty("totalCharacterCount") final Count<Long> totalCharacterCount,
                           @JsonProperty("totalBytes") final Long totalBytes,
                           @JsonProperty("availableChildStreamTypes") final Set<String> availableChildStreamTypes,
                           @JsonProperty("data") final String data,
                           @JsonProperty("html") final boolean html,
                           @JsonProperty("dataType") final DataType dataType,
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
        this.data = data;
        this.html = html;
        this.dataType = dataType;
        this.totalBytes = totalBytes;
    }

    public String getData() {
        return data;
    }

    public boolean isHtml() {
        return html;
    }

    public DataType getDataType() {
        return dataType;
    }

    public Long getTotalBytes() {
        return totalBytes;
    }

    @JsonIgnore
    public Optional<Long> getOptTotalBytes() {
        return Optional.ofNullable(totalBytes);
    }
}
