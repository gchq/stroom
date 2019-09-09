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

import org.apache.solr.common.params.SolrParams;
import stroom.pipeline.errorhandler.ErrorReceiver;
import stroom.search.extraction.Values;
import stroom.search.solr.CachedSolrIndex;

public class SolrSearchTask {
    private final CachedSolrIndex solrIndex;
    private final SolrParams solrParams;
    private final String[] fieldNames;
    private final ResultReceiver resultReceiver;
    private final ErrorReceiver errorReceiver;

    public SolrSearchTask(final CachedSolrIndex solrIndex,
                          final SolrParams solrParams,
                          final String[] fieldNames,
                          final ResultReceiver resultReceiver,
                          final ErrorReceiver errorReceiver) {
        this.solrIndex = solrIndex;
        this.solrParams = solrParams;
        this.fieldNames = fieldNames;
        this.resultReceiver = resultReceiver;
        this.errorReceiver = errorReceiver;
    }

    public CachedSolrIndex getSolrIndex() {
        return solrIndex;
    }

    public SolrParams getSolrParams() {
        return solrParams;
    }

    public String[] getFieldNames() {
        return fieldNames;
    }

    public ResultReceiver getResultReceiver() {
        return resultReceiver;
    }

    public ErrorReceiver getErrorReceiver() {
        return errorReceiver;
    }

    public interface ResultReceiver {
        void receive(Values values);
    }
}
