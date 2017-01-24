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

package stroom.search.server;

import org.springframework.stereotype.Component;
import stroom.datasource.api.DataSource;
import stroom.datasource.api.DataSourceField;
import stroom.index.shared.Index;
import stroom.index.shared.IndexService;
import stroom.query.api.SearchRequest;
import stroom.query.api.SearchResponse;

import javax.annotation.Resource;
import java.util.List;

@Component
public class LuceneIndexDataSourceProvider {
    public static final String ENTITY_TYPE = Index.ENTITY_TYPE;

    @Resource
    private IndexService indexService;

    public DataSource getDataSource(final String uuid) {
        final Index index = indexService.loadByUuid(uuid);

        return new DataSource(IndexDataSourceFieldUtil.getDataSourceFields(index));
    }

//    @Override
//    public String getType() {
//        return ENTITY_TYPE;
//    }
}
