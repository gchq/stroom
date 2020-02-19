/*
 * Copyright 2017 Crown Copyright
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

package stroom.dashboard.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_DEFAULT)
public class DownloadSearchResultsRequest {
    @JsonProperty
    private final String applicationInstanceId;
    @JsonProperty
    private final SearchRequest searchRequest;
    @JsonProperty
    private final String componentId;
    @JsonProperty
    private final DownloadSearchResultFileType fileType;
    @JsonProperty
    private final boolean sample;
    @JsonProperty
    private final int percent;
    @JsonProperty
    private final String dateTimeLocale;

    @JsonCreator
    public DownloadSearchResultsRequest(@JsonProperty("applicationInstanceId") final String applicationInstanceId,
                                        @JsonProperty("searchRequest") final SearchRequest searchRequest,
                                        @JsonProperty("componentId") final String componentId,
                                        @JsonProperty("fileType") final DownloadSearchResultFileType fileType,
                                        @JsonProperty("sample") final boolean sample,
                                        @JsonProperty("percent") final int percent,
                                        @JsonProperty("dateTimeLocale") final String dateTimeLocale) {
        this.applicationInstanceId = applicationInstanceId;
        this.searchRequest = searchRequest;
        this.componentId = componentId;
        this.fileType = fileType;
        this.sample = sample;
        this.percent = percent;
        this.dateTimeLocale = dateTimeLocale;
    }

    public String getApplicationInstanceId() {
        return applicationInstanceId;
    }

    public SearchRequest getSearchRequest() {
        return searchRequest;
    }

    public String getComponentId() {
        return componentId;
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

    public String getDateTimeLocale() {
        return dateTimeLocale;
    }
}
