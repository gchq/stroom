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
import stroom.index.shared.Index;
import stroom.index.shared.IndexService;
import stroom.query.SearchResponseCreator;
import stroom.query.api.DocRef;
import stroom.query.api.QueryKey;
import stroom.query.api.SearchRequest;
import stroom.query.api.SearchResponse;

import javax.inject.Inject;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SearchService {
    private final LuceneSearchStoreFactory luceneSearchStoreFactory;
    private final IndexService indexService;

    // TODO : Replace this with a proper cache. Also add possibility of terminating searches.
    private Map<QueryKey, SearchResponseCreator> searchResultCreatorCache = new ConcurrentHashMap<>();

    @Inject
    public SearchService(final LuceneSearchStoreFactory luceneSearchStoreFactory, final IndexService indexService) {
        this.luceneSearchStoreFactory = luceneSearchStoreFactory;
        this.indexService = indexService;
    }

    public DataSource getDataSource(final DocRef docRef) {
        final Index index = indexService.loadByUuid(docRef.getUuid());
        return new DataSource(IndexDataSourceFieldUtil.getDataSourceFields(index));
    }

    public SearchResponse search(final SearchRequest request) {
        final SearchResponseCreator searchResponseCreator = searchResultCreatorCache.computeIfAbsent(request.getKey(), k -> new SearchResponseCreator(luceneSearchStoreFactory.create(request)));

        return searchResponseCreator.create(request);
    }

    public void terminate(final QueryKey queryKey) {
        final SearchResponseCreator searchResponseCreator = searchResultCreatorCache.remove(queryKey);
        if (searchResponseCreator != null) {
            searchResponseCreator.destroy();
        }
    }
}