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
import stroom.search.server.SearchResultCreatorManager.Key;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Path("/index")
public class SearchResource {
    private final SearchResultCreatorManager searchResultCreatorManager;
    private final IndexService indexService;

    @Inject
    public SearchResource(final SearchResultCreatorManager searchResultCreatorManager, final IndexService indexService) {
        this.searchResultCreatorManager = searchResultCreatorManager;
        this.indexService = indexService;
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/dataSource")
    public DataSource getDataSource(final DocRef docRef) {
        final Index index = indexService.loadByUuid(docRef.getUuid());
        return new DataSource(IndexDataSourceFieldUtil.getDataSourceFields(index));
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/search")
    public SearchResponse search(final SearchRequest request) {
//        final SearchResponseCreator searchResponseCreator = searchResultCreatorCache.computeIfAbsent(request.getKey(), k -> new SearchResponseCreator(luceneSearchStoreFactory.create(request)));

        final SearchResponseCreator searchResponseCreator = searchResultCreatorManager.get(new Key(request));
        return searchResponseCreator.create(request);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/destroy")
    public Boolean destroy(final QueryKey queryKey) {
        searchResultCreatorManager.remove(new Key(queryKey));
        return Boolean.TRUE;
    }
}