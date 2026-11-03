/*
 * Copyright 2019 Crown Copyright
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

package stroom.search.elastic.search;

import stroom.search.elastic.suggest.ElasticSuggestConfig;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder(alphabetic = true)
public class ElasticSearchConfig extends AbstractConfig implements IsStroomConfig {

    private static final boolean DEFAULT_HIGHLIGHT = true;
    private static final int DEFAULT_FIELD_STATS_SAMPLE_SIZE = 5_000;
    private static final int DEFAULT_FIELD_STATS_MAX_SAMPLE_SIZE = 50_000;

    private final boolean highlight;
    private final StroomDuration scrollDuration;
    private final String storeSize;
    private final ElasticSuggestConfig suggestConfig;
    private final int fieldStatsSampleSize;
    private final int fieldStatsMaxSampleSize;

    public ElasticSearchConfig() {
        highlight = DEFAULT_HIGHLIGHT;
        scrollDuration = StroomDuration.ofMinutes(1);
        storeSize = "1000000,100,10,1";
        suggestConfig = new ElasticSuggestConfig();
        fieldStatsSampleSize = DEFAULT_FIELD_STATS_SAMPLE_SIZE;
        fieldStatsMaxSampleSize = DEFAULT_FIELD_STATS_MAX_SAMPLE_SIZE;
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public ElasticSearchConfig(@JsonProperty("highlight") final Boolean highlight,
                               @JsonProperty("scrollDuration") final StroomDuration scrollDuration,
                               @JsonProperty("storeSize") final String storeSize,
                               @JsonProperty("suggestions") final ElasticSuggestConfig suggestConfig,
                               @JsonProperty("fieldStatsSampleSize") final Integer fieldStatsSampleSize,
                               @JsonProperty("fieldStatsMaxSampleSize") final Integer fieldStatsMaxSampleSize) {
        this.highlight = Objects.requireNonNullElse(highlight, DEFAULT_HIGHLIGHT);
        this.scrollDuration = scrollDuration;
        this.storeSize = storeSize;
        this.suggestConfig = suggestConfig;
        this.fieldStatsSampleSize = Objects.requireNonNullElse(fieldStatsSampleSize, DEFAULT_FIELD_STATS_SAMPLE_SIZE);
        this.fieldStatsMaxSampleSize = Objects.requireNonNullElse(
                fieldStatsMaxSampleSize, DEFAULT_FIELD_STATS_MAX_SAMPLE_SIZE);
    }

    @JsonPropertyDescription("Highlight matched terms in the source document.")
    public boolean getHighlight() {
        return highlight;
    }

    @JsonPropertyDescription("Amount of time to allow an Elasticsearch scroll request to continue before aborting.")
    public StroomDuration getScrollDuration() {
        return scrollDuration;
    }

    @JsonPropertyDescription("The maximum number of search results to keep in memory at each level.")
    public String getStoreSize() {
        return storeSize;
    }

    @JsonProperty("suggestions")
    public ElasticSuggestConfig getSuggestConfig() {
        return suggestConfig;
    }

    @JsonPropertyDescription("The default number of documents sampled per shard when showing the statistics of an " +
                             "Elasticsearch index field.")
    public int getFieldStatsSampleSize() {
        return fieldStatsSampleSize;
    }

    @JsonPropertyDescription("The largest number of documents a user may ask to sample per shard when showing the " +
                             "statistics of an Elasticsearch index field.")
    public int getFieldStatsMaxSampleSize() {
        return fieldStatsMaxSampleSize;
    }

    @Override
    public String toString() {
        return "ElasticSearchConfig{" +
                "highlight=" + highlight +
                ", scrollDuration='" + scrollDuration + "'" +
                ", storeSize=" + storeSize +
                ", suggestConfig=" + suggestConfig +
                ", fieldStatsSampleSize=" + fieldStatsSampleSize +
                ", fieldStatsMaxSampleSize=" + fieldStatsMaxSampleSize +
                '}';
    }
}
