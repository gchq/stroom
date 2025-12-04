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

package stroom.search.solr;

import stroom.search.solr.search.SolrSearchConfig;
import stroom.util.cache.CacheConfig;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


@JsonPropertyOrder(alphabetic = true)
public class SolrConfig extends AbstractConfig implements IsStroomConfig {

    private final SolrSearchConfig solrSearchConfig;
    private final CacheConfig indexClientCache;
    private final CacheConfig indexCache;

    public SolrConfig() {
        solrSearchConfig = new SolrSearchConfig();
        indexClientCache = CacheConfig.builder()
                .maximumSize(100L)
                .expireAfterAccess(StroomDuration.ofMinutes(10))
                .build();
        indexCache = CacheConfig.builder()
                .maximumSize(100L)
                .expireAfterWrite(StroomDuration.ofMinutes(10))
                .build();
    }

    @JsonCreator
    public SolrConfig(@JsonProperty("search") final SolrSearchConfig solrSearchConfig,
                      @JsonProperty("indexClientCache") final CacheConfig indexClientCache,
                      @JsonProperty("indexCache") final CacheConfig indexCache) {
        this.solrSearchConfig = solrSearchConfig;
        this.indexClientCache = indexClientCache;
        this.indexCache = indexCache;
    }

    @JsonProperty("search")
    public SolrSearchConfig getSolrSearchConfig() {
        return solrSearchConfig;
    }

    public CacheConfig getIndexClientCache() {
        return indexClientCache;
    }

    public CacheConfig getIndexCache() {
        return indexCache;
    }

    @Override

    public String toString() {
        return "SolrConfig{" +
                "solrSearchConfig=" + solrSearchConfig +
                ", indexClientCache=" + indexClientCache +
                ", indexCache=" + indexCache +
                '}';
    }
}
