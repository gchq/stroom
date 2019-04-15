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

package stroom.search.impl;

import com.codahale.metrics.annotation.Timed;
import stroom.datasource.api.v2.DataSource;
import stroom.docref.DocRef;
import stroom.index.impl.IndexStore;
import stroom.index.impl.StroomIndexQueryResource;
import stroom.index.shared.IndexDoc;
import stroom.query.api.v2.QueryKey;
import stroom.query.api.v2.SearchRequest;
import stroom.query.api.v2.SearchResponse;
import stroom.query.common.v2.SearchResponseCreator;
import stroom.query.common.v2.SearchResponseCreatorCache;
import stroom.query.common.v2.SearchResponseCreatorManager;
import stroom.search.impl.IndexDataSourceFieldUtil;
import stroom.search.impl.LuceneSearchResponseCreatorManager;
import stroom.security.api.Security;

import javax.inject.Inject;

public class StroomIndexQueryResourceImpl implements StroomIndexQueryResource {
    private final SearchResponseCreatorManager searchResponseCreatorManager;
    private final IndexStore indexStore;
    private final Security security;

    @Inject
    public StroomIndexQueryResourceImpl(final LuceneSearchResponseCreatorManager searchResponseCreatorManager,
                                        final IndexStore indexStore,
                                        final Security security) {
        this.searchResponseCreatorManager = searchResponseCreatorManager;
        this.indexStore = indexStore;
        this.security = security;
    }

    @Timed
    public DataSource getDataSource(final DocRef docRef) {
        return security.useAsReadResult(() -> {
            final IndexDoc index = indexStore.readDocument(docRef);
            return new DataSource(IndexDataSourceFieldUtil.getDataSourceFields(index));
        });
    }

    @Timed
    public SearchResponse search(final SearchRequest request) {

        //if this is the first call for this query key then it will create a searchResponseCreator (& store) that have
        //a lifespan beyond the scope of this request and then begin the search for the data
        //If it is not the first call for this query key then it will return the existing searchResponseCreator with
        //access to whatever data has been found so far
        final SearchResponseCreator searchResponseCreator = searchResponseCreatorManager.get(new SearchResponseCreatorCache.Key(request));

        //create a response from the data found so far, this could be complete/incomplete
        return searchResponseCreator.create(request);
    }

    @Timed
    public Boolean destroy(final QueryKey queryKey) {
        searchResponseCreatorManager.remove(new SearchResponseCreatorCache.Key(queryKey));
        return Boolean.TRUE;
    }
}