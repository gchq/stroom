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

package stroom.statistics.server.sql.search;

import com.codahale.metrics.annotation.Timed;
import com.codahale.metrics.health.HealthCheck;
import com.codahale.metrics.health.HealthCheck.Result;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import stroom.datasource.api.v2.DataSource;
import stroom.util.HasHealthCheck;
import stroom.query.api.v2.DocRef;
import stroom.query.api.v2.QueryKey;
import stroom.query.api.v2.SearchRequest;
import stroom.query.api.v2.SearchResponse;
import stroom.statistics.server.sql.StatisticsQueryService;
import stroom.util.json.JsonUtil;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Api(
        value = "sqlstatistics query - /v2",
        description = "Stroom SQL Statistics Query API")
@Path("/sqlstatistics/v2")
@Produces(MediaType.APPLICATION_JSON)
@Component
public class SqlStatisticsQueryResource implements HasHealthCheck {
    private static final Logger LOGGER = LoggerFactory.getLogger(SqlStatisticsQueryResource.class);

    private final StatisticsQueryService statisticsQueryService;

    @Inject
    public SqlStatisticsQueryResource(final StatisticsQueryService statisticsQueryService) {
        this.statisticsQueryService = statisticsQueryService;
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

        if (LOGGER.isDebugEnabled()) {
            String json = JsonUtil.writeValueAsString(docRef);
            LOGGER.debug("/dataSource called with docRef:\n{}", json);
        }
        return statisticsQueryService.getDataSource(docRef);
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
        if (LOGGER.isDebugEnabled()) {
            String json = JsonUtil.writeValueAsString(request);
            LOGGER.debug("/search called with searchRequest:\n{}", json);
        }

        return statisticsQueryService.search(request);
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
        if (LOGGER.isDebugEnabled()) {
            String json = JsonUtil.writeValueAsString(queryKey);
            LOGGER.debug("/destroy called with queryKey:\n{}", json);
        }
        return statisticsQueryService.destroy(queryKey);
    }

    @Override
    public Result getHealth() {
        return HealthCheck.Result.healthy();
    }
}