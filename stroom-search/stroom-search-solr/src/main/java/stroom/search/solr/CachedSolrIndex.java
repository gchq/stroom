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

package stroom.search.solr;

import stroom.search.solr.shared.SolrIndex;
import stroom.search.solr.shared.SolrIndexField;

import java.util.List;
import java.util.Map;

public class CachedSolrIndex {
    private final SolrIndex index;
    private final List<SolrIndexField> fields;
    private final Map<String, SolrIndexField> fieldsMap;

    CachedSolrIndex(final SolrIndex index, final List<SolrIndexField> fields, final Map<String, SolrIndexField> fieldsMap) {
        this.index = index;
        this.fields = fields;
        this.fieldsMap = fieldsMap;
    }

    public SolrIndex getIndex() {
        return index;
    }

    public List<SolrIndexField> getFields() {
        return fields;
    }

    public Map<String, SolrIndexField> getFieldsMap() {
        return fieldsMap;
    }
}
