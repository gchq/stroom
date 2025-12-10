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

package stroom.search.elastic;

import stroom.query.api.datasource.QueryField;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.search.elastic.shared.ElasticIndexField;

import java.util.List;
import java.util.Map;

public interface ElasticIndexService {

    List<QueryField> getDataSourceFields(ElasticIndexDoc index);

    List<ElasticIndexField> getFields(ElasticIndexDoc index);

    Map<String, ElasticIndexField> getFieldsMap(ElasticIndexDoc index);
}
