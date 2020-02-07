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

public class DownloadSearchResultsRequest {
    private String applicationInstanceId;
    private DashboardQueryKey queryKey;
    private SearchRequest searchRequest;
    private String componentId;
    private DownloadSearchResultFileType fileType;
    private boolean sample;
    private int percent;
    private String dateTimeLocale;

    public DownloadSearchResultsRequest() {
        // Default constructor necessary for GWT serialisation.
    }

    public DownloadSearchResultsRequest(final String applicationInstanceId,
                                        final DashboardQueryKey queryKey,
                                        final SearchRequest searchRequest,
                                        final String componentId,
                                        final DownloadSearchResultFileType fileType,
                                        final boolean sample,
                                        final int percent,
                                        final String dateTimeLocale) {
        this.applicationInstanceId = applicationInstanceId;
        this.queryKey = queryKey;
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

    public void setApplicationInstanceId(final String applicationInstanceId) {
        this.applicationInstanceId = applicationInstanceId;
    }

    public DashboardQueryKey getQueryKey() {
        return queryKey;
    }

    public void setQueryKey(final DashboardQueryKey queryKey) {
        this.queryKey = queryKey;
    }

    public SearchRequest getSearchRequest() {
        return searchRequest;
    }

    public void setSearchRequest(final SearchRequest searchRequest) {
        this.searchRequest = searchRequest;
    }

    public String getComponentId() {
        return componentId;
    }

    public void setComponentId(final String componentId) {
        this.componentId = componentId;
    }

    public DownloadSearchResultFileType getFileType() {
        return fileType;
    }

    public void setFileType(final DownloadSearchResultFileType fileType) {
        this.fileType = fileType;
    }

    public boolean isSample() {
        return sample;
    }

    public void setSample(final boolean sample) {
        this.sample = sample;
    }

    public int getPercent() {
        return percent;
    }

    public void setPercent(final int percent) {
        this.percent = percent;
    }

    public String getDateTimeLocale() {
        return dateTimeLocale;
    }

    public void setDateTimeLocale(final String dateTimeLocale) {
        this.dateTimeLocale = dateTimeLocale;
    }
}
