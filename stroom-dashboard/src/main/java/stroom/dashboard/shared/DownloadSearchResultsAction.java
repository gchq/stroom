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

package stroom.dashboard.shared;

import stroom.dispatch.shared.Action;
import stroom.query.shared.QueryKey;
import stroom.query.shared.Search;
import stroom.util.shared.ResourceGeneration;

public class DownloadSearchResultsAction extends Action<ResourceGeneration> {
    private static final long serialVersionUID = -6668626615097471925L;

    private QueryKey queryKey;
    private Search search;
    private String componentId;
    private DownloadSearchResultFileType fileType;
    private boolean sample;
    private int percent;
    private String dateTimeLocale;

    public DownloadSearchResultsAction() {
        // Default constructor necessary for GWT serialisation.
    }

    public DownloadSearchResultsAction(final QueryKey queryKey, final Search search, final String componentId,
            final DownloadSearchResultFileType fileType, final boolean sample, final int percent,
            final String dateTimeLocale) {
        this.queryKey = queryKey;
        this.search = search;
        this.componentId = componentId;
        this.fileType = fileType;
        this.sample = sample;
        this.percent = percent;
        this.dateTimeLocale = dateTimeLocale;
    }

    public QueryKey getQueryKey() {
        return queryKey;
    }

    public Search getSearch() {
        return search;
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

    @Override
    public String getTaskName() {
        return "Download Search Results";
    }
}
