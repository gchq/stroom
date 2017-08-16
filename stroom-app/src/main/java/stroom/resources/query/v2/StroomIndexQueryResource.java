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

package stroom.resources.query.v2;

import com.codahale.metrics.annotation.Timed;
import com.codahale.metrics.health.HealthCheck;
import stroom.datasource.api.v2.DataSource;
import stroom.index.shared.Index;
import stroom.index.shared.IndexService;
import stroom.query.v2.SearchResponseCreator;
import stroom.query.api.v2.DocRef;
import stroom.query.api.v2.QueryKey;
import stroom.query.api.v2.SearchRequest;
import stroom.query.api.v2.SearchResponse;
import stroom.resources.ResourcePaths;
import stroom.search.server.IndexDataSourceFieldUtil;
import stroom.search.server.SearchResultCreatorManager;
import stroom.search.server.SearchResultCreatorManager.Key;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Path(ResourcePaths.STROOM_INDEX + ResourcePaths.V1)
@Produces(MediaType.APPLICATION_JSON)
public class StroomIndexQueryResource implements QueryResource {

    private SearchResultCreatorManager searchResultCreatorManager;
    private IndexService indexService;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path(QueryResource.DATA_SOURCE_ENDPOINT)
    @Timed
    public DataSource getDataSource(final DocRef docRef) {
        final Index index = indexService.loadByUuid(docRef.getUuid());
        return new DataSource(IndexDataSourceFieldUtil.getDataSourceFields(index));
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path(QueryResource.SEARCH_ENDPOINT)
    @Timed
    public SearchResponse search(final SearchRequest request) {
        final SearchResponseCreator searchResponseCreator = searchResultCreatorManager.getOrCreate(new Key(request));
        return searchResponseCreator.create(request);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path(QueryResource.DESTROY_ENDPOINT)
    @Timed
    public Boolean destroy(final QueryKey queryKey) {
        searchResultCreatorManager.remove(new Key(queryKey));
        return Boolean.TRUE;
    }

    public void setSearchResultCreatorManager(SearchResultCreatorManager searchResultCreatorManager) {
        this.searchResultCreatorManager = searchResultCreatorManager;
    }

    public void setIndexService(IndexService indexService) {
        this.indexService = indexService;
    }

    public HealthCheck.Result getHealth() {
        if (searchResultCreatorManager == null || indexService == null) {
            StringBuilder errorMessageBuilder = new StringBuilder();
            errorMessageBuilder.append("Dependency error!");
            String searchResultCreatorManagerMessage = " 'searchResultCreatorManager' has not been set!";
            String indexServiceMessage = " 'indexService' has not been set!";
            if (searchResultCreatorManager == null) {
                errorMessageBuilder.append(searchResultCreatorManagerMessage);
            }
            if (indexService == null) {
                errorMessageBuilder.append(indexServiceMessage);
            }
            return HealthCheck.Result.unhealthy(errorMessageBuilder.toString());
        } else {
            return HealthCheck.Result.healthy();
        }
    }
}