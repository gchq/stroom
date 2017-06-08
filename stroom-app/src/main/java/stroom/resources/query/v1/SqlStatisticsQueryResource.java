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

package stroom.resources.query.v1;

import com.codahale.metrics.annotation.Timed;
import com.codahale.metrics.health.HealthCheck;
import stroom.datasource.api.v1.DataSource;
import stroom.query.api.v1.DocRef;
import stroom.query.api.v1.QueryKey;
import stroom.query.api.v1.SearchRequest;
import stroom.query.api.v1.SearchResponse;
import stroom.resources.ResourcePaths;
import stroom.statistics.server.sql.StatisticsQueryService;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Path(ResourcePaths.SQL_STATISTICS + ResourcePaths.V1)
@Produces(MediaType.APPLICATION_JSON)
public class SqlStatisticsQueryResource implements QueryResource {
    private StatisticsQueryService statisticsQueryService;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path(QueryResource.DATASOURCE_ENDPOINT)
    @Timed
    public DataSource getDataSource(final DocRef docRef) {
        return statisticsQueryService.getDataSource(docRef);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path(QueryResource.SEARCH_ENDPOINT)
    @Timed
    public SearchResponse search(final SearchRequest request) {
        return statisticsQueryService.search(request);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path(QueryResource.DESTROY_ENDPOINT)
    @Timed
    public Boolean destroy(final QueryKey queryKey) {
        return statisticsQueryService.destroy(queryKey);
    }

    public void setStatisticsQueryService(final StatisticsQueryService statisticsQueryService) {
        this.statisticsQueryService = statisticsQueryService;
    }

    public HealthCheck.Result getHealth() {
        if (statisticsQueryService == null) {
            StringBuilder errorMessageBuilder = new StringBuilder();
            errorMessageBuilder.append("Dependency error!");
            String statisticsQueryServiceMessage = " 'statisticsQueryService' has not been set!";
            errorMessageBuilder.append(statisticsQueryServiceMessage);
            return HealthCheck.Result.unhealthy(errorMessageBuilder.toString());
        } else {
            return HealthCheck.Result.healthy();
        }
    }
}