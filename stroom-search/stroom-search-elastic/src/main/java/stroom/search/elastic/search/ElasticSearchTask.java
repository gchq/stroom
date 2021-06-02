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

package stroom.search.elastic.search;

import stroom.search.coprocessor.Receiver;
import stroom.search.elastic.shared.ElasticIndex;

import org.elasticsearch.index.query.QueryBuilder;

public class ElasticSearchTask {
    private final ElasticIndex elasticIndex;
    private final QueryBuilder query;
    private final String[] fieldNames;
    private final Receiver receiver;
    private final Tracker tracker;
    private final ElasticSearchResultCollector resultCollector;

    ElasticSearchTask(final ElasticIndex elasticIndex,
                      final QueryBuilder query,
                      final String[] fieldNames,
                      final Receiver receiver,
                      final Tracker tracker,
                      final ElasticSearchResultCollector resultCollector) {
        this.elasticIndex = elasticIndex;
        this.query = query;
        this.fieldNames = fieldNames;
        this.receiver = receiver;
        this.tracker = tracker;
        this.resultCollector = resultCollector;
    }

    ElasticIndex getElasticIndex() {
        return elasticIndex;
    }

    QueryBuilder getQuery() { return query; }

    String[] getFieldNames() {
        return fieldNames;
    }

    Receiver getReceiver() {
        return receiver;
    }

    Tracker getTracker() {
        return tracker;
    }

    ElasticSearchResultCollector getResultCollector() {
        return resultCollector;
    }
}