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

package stroom.search.solr.search;

import stroom.query.api.v2.Query;
import stroom.query.common.v2.CoprocessorSettings;
import stroom.query.common.v2.CoprocessorSettingsMap.CoprocessorKey;
import stroom.search.resultsender.NodeResult;
import stroom.search.solr.CachedSolrIndex;
import stroom.task.api.ServerTask;

import java.util.Map;

class SolrClusterSearchTask extends ServerTask<NodeResult> {
    private static final long serialVersionUID = -1305243739417365803L;

    private final CachedSolrIndex cachedSolrIndex;
    private final Query query;
    private final int resultSendFrequency;
    private final String[] storedFields;
    private final Map<CoprocessorKey, CoprocessorSettings> coprocessorMap;
    private final String dateTimeLocale;
    private final long now;

    SolrClusterSearchTask(final CachedSolrIndex cachedSolrIndex,
                          final Query query,
                          final int resultSendFrequency,
                          final String[] storedFields,
                          final Map<CoprocessorKey, CoprocessorSettings> coprocessorMap,
                          final String dateTimeLocale,
                          final long now) {
        this.cachedSolrIndex = cachedSolrIndex;
        this.query = query;
        this.resultSendFrequency = resultSendFrequency;
        this.storedFields = storedFields;
        this.coprocessorMap = coprocessorMap;
        this.dateTimeLocale = dateTimeLocale;
        this.now = now;
    }

    public CachedSolrIndex getCachedSolrIndex() {
        return cachedSolrIndex;
    }

    public Query getQuery() {
        return query;
    }

    public int getResultSendFrequency() {
        return resultSendFrequency;
    }

    public String[] getStoredFields() {
        return storedFields;
    }

    public Map<CoprocessorKey, CoprocessorSettings> getCoprocessorMap() {
        return coprocessorMap;
    }

    public String getDateTimeLocale() {
        return dateTimeLocale;
    }

    public long getNow() {
        return now;
    }
}
