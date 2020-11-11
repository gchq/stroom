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
import stroom.search.solr.CachedSolrIndex;

import java.util.List;

class SolrClusterSearchTask {
    private final CachedSolrIndex cachedSolrIndex;
    private final Query query;
    private final String[] storedFields;
    private final List<CoprocessorSettings> settings;
    private final String dateTimeLocale;
    private final long now;

    SolrClusterSearchTask(final CachedSolrIndex cachedSolrIndex,
                          final Query query,
                          final String[] storedFields,
                          final List<CoprocessorSettings> settings,
                          final String dateTimeLocale,
                          final long now) {
        this.cachedSolrIndex = cachedSolrIndex;
        this.query = query;
        this.storedFields = storedFields;
        this.settings = settings;
        this.dateTimeLocale = dateTimeLocale;
        this.now = now;
    }

    public CachedSolrIndex getCachedSolrIndex() {
        return cachedSolrIndex;
    }

    public Query getQuery() {
        return query;
    }

    public String[] getStoredFields() {
        return storedFields;
    }

    public List<CoprocessorSettings> getSettings() {
        return settings;
    }

    public String getDateTimeLocale() {
        return dateTimeLocale;
    }

    public long getNow() {
        return now;
    }
}
