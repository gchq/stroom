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

package stroom.query.api;

import java.util.List;

public class TableSettingsBuilder {
    private String queryId;
    private List<Field> fields;
    private Boolean extractValues;
    private DocRef extractionPipeline;
    private List<Integer> maxResults;
    private Boolean showDetail;

    public TableSettingsBuilder queryId(final String queryId) {
        this.queryId = queryId;
        return this;
    }

    public TableSettingsBuilder fields(final List<Field> fields) {
        this.fields = fields;
        return this;
    }

    public TableSettingsBuilder extractValues(final Boolean extractValues) {
        this.extractValues = extractValues;
        return this;
    }

    public TableSettingsBuilder extractionPipeline(final DocRef extractionPipeline) {
        this.extractionPipeline = extractionPipeline;
        return this;
    }

    public TableSettingsBuilder maxResults(final List<Integer> maxResults) {
        this.maxResults = maxResults;
        return this;
    }

    public TableSettingsBuilder showDetail(final Boolean showDetail) {
        this.showDetail = showDetail;
        return this;
    }

    public TableSettings build() {
        return new TableSettings(queryId, fields, extractValues, extractionPipeline, maxResults, showDetail);
    }
}