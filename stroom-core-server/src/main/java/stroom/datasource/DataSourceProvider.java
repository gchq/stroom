/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.datasource;

import stroom.datasource.api.v1.DataSource;
import stroom.query.api.v1.DocRef;
import stroom.query.api.v1.QueryKey;
import stroom.query.api.v1.SearchRequest;
import stroom.query.api.v1.SearchResponse;

public interface DataSourceProvider {
    DataSource getDataSource(DocRef docRef);

    SearchResponse search(SearchRequest request);

    Boolean destroy(QueryKey queryKey);

    String getType();
}
