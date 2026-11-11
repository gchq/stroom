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

package stroom.query.shared;

import stroom.dashboard.shared.DownloadSearchResultFileType;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class DownloadQueryResultsRequest {

    @JsonProperty
    private final QuerySearchRequest searchRequest;
    @JsonProperty
    private final DownloadSearchResultFileType fileType;
    @JsonProperty
    private final boolean sample;
    @JsonProperty
    private final int percent;
    @JsonProperty
    private final boolean includeSourceIds;

    public DownloadQueryResultsRequest(final QuerySearchRequest searchRequest,
                                       final DownloadSearchResultFileType fileType,
                                       final Boolean sample,
                                       final Integer percent) {
        this(searchRequest, fileType, sample, percent, false);
    }

    /// @param includeSourceIds True to also write each row's stream and event ids (the hidden StreamId and EventId
    ///                         columns), so each row can be traced to the event it came from.
    @JsonCreator
    public DownloadQueryResultsRequest(@JsonProperty("searchRequest") final QuerySearchRequest searchRequest,
                                       @JsonProperty("fileType") final DownloadSearchResultFileType fileType,
                                       @JsonProperty("sample") final Boolean sample,
                                       @JsonProperty("percent") final Integer percent,
                                       @JsonProperty("includeSourceIds") final Boolean includeSourceIds) {
        this.includeSourceIds = Objects.requireNonNullElse(includeSourceIds, false);
        this.searchRequest = searchRequest;
        this.fileType = fileType;
        this.sample = Objects.requireNonNullElse(sample, false);
        this.percent = Objects.requireNonNullElse(percent, 0);
    }

    public QuerySearchRequest getSearchRequest() {
        return searchRequest;
    }

    public DownloadSearchResultFileType getFileType() {
        return fileType;
    }

    public boolean isSample() {
        return sample;
    }

    public int getPercent() {
        return percent;
    }

    public boolean isIncludeSourceIds() {
        return includeSourceIds;
    }
}
