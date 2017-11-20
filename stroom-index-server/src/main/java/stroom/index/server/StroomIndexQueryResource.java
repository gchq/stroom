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

package stroom.index.server;

import com.codahale.metrics.annotation.Timed;
import com.codahale.metrics.health.HealthCheck;
import com.codahale.metrics.health.HealthCheck.Result;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.stereotype.Component;
import stroom.datasource.api.v2.DataSource;
import stroom.index.shared.Index;
import stroom.query.api.v2.DocRef;
import stroom.query.api.v2.QueryKey;
import stroom.query.api.v2.SearchRequest;
import stroom.query.api.v2.SearchResponse;
import stroom.query.common.v2.SearchResponseCreator;
import stroom.search.server.IndexDataSourceFieldUtil;
import stroom.search.server.SearchResultCreatorManager;
import stroom.search.server.SearchResultCreatorManager.Key;
import stroom.security.SecurityContext;
import stroom.security.SecurityHelper;
import stroom.util.HasHealthCheck;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Api(
        value = "stroom-index query - /v2",
        description = "Stroom Index Query API")
@Path("/stroom-index/v2")
@Produces(MediaType.APPLICATION_JSON)
@Component
public class StroomIndexQueryResource implements HasHealthCheck {
    private final SearchResultCreatorManager searchResultCreatorManager;
    private final IndexService indexService;
    private final SecurityContext securityContext;

    @Inject
    public StroomIndexQueryResource(final SearchResultCreatorManager searchResultCreatorManager,
                                    final IndexService indexService,
                                    final SecurityContext securityContext) {
        this.searchResultCreatorManager = searchResultCreatorManager;
        this.indexService = indexService;
        this.securityContext = securityContext;
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/dataSource")
    @Timed
    @ApiOperation(
            value = "Submit a request for a data source definition, supplying the DocRef for the data source",
            response = DataSource.class)
    public DataSource getDataSource(@ApiParam("DocRef") final DocRef docRef) {
        try (final SecurityHelper securityHelper = SecurityHelper.elevate(securityContext)) {
            final Index index = indexService.loadByUuid(docRef.getUuid());
            return new DataSource(IndexDataSourceFieldUtil.getDataSourceFields(index));
        }
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/search")
    @Timed
    @ApiOperation(
            value = "Submit a search request",
            response = SearchResponse.class)
    public SearchResponse search(@ApiParam("SearchRequest") final SearchRequest request) {
        final SearchResponseCreator searchResponseCreator = searchResultCreatorManager.get(new Key(request));
        return searchResponseCreator.create(request);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/destroy")
    @Timed
    @ApiOperation(
            value = "Destroy a running query",
            response = Boolean.class)
    public Boolean destroy(@ApiParam("QueryKey") final QueryKey queryKey) {
        searchResultCreatorManager.remove(new Key(queryKey));
        return Boolean.TRUE;
    }

    @Override
    public Result getHealth() {
        return HealthCheck.Result.healthy();
    }
}