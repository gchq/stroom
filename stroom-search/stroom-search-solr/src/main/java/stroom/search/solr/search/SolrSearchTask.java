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

package stroom.search.solr.search;

import stroom.query.common.v2.Receiver;
import stroom.search.solr.CachedSolrIndex;

import org.apache.solr.common.params.SolrParams;

class SolrSearchTask {
    private final CachedSolrIndex solrIndex;
    private final SolrParams solrParams;
    private final String[] fieldNames;
    private final Receiver receiver;
    private final Tracker tracker;

    SolrSearchTask(final CachedSolrIndex solrIndex,
                   final SolrParams solrParams,
                   final String[] fieldNames,
                   final Receiver receiver,
                   final Tracker tracker) {
        this.solrIndex = solrIndex;
        this.solrParams = solrParams;
        this.fieldNames = fieldNames;
        this.receiver = receiver;
        this.tracker = tracker;
    }

    CachedSolrIndex getSolrIndex() {
        return solrIndex;
    }

    SolrParams getSolrParams() {
        return solrParams;
    }

    String[] getFieldNames() {
        return fieldNames;
    }

    Receiver getReceiver() {
        return receiver;
    }

    Tracker getTracker() {
        return tracker;
    }
}
