/*
 * Copyright 2018 Crown Copyright
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

package stroom.search.elastic.shared;

import stroom.entity.shared.Action;
import stroom.util.shared.SharedList;
import stroom.util.shared.SharedString;

public class FetchElasticFieldsAction extends Action<SharedList<SharedString>> {
    private static final long serialVersionUID = -3560107233301674555L;

    private ElasticIndex elasticIndex;

    public FetchElasticFieldsAction() { }

    public FetchElasticFieldsAction(final ElasticIndex elasticIndex) {
        this.elasticIndex = elasticIndex;
    }

    public ElasticIndex getElasticIndex() {
        return elasticIndex;
    }

    @Override
    public String getTaskName() {
        return "Fetch Elasticsearch index fields";
    }
}
