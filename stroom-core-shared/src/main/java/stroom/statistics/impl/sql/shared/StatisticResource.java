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

package stroom.statistics.impl.sql.shared;

import stroom.docref.DocRef;
import stroom.pipeline.shared.XsltDoc;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.fusesource.restygwt.client.DirectRestService;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Api(value = "statistic - /v1")
@Path("/statistic" + ResourcePaths.V1)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface StatisticResource extends RestResource, DirectRestService {

    @POST
    @Path("/read")
    @ApiOperation(
            value = "Get a statistic doc",
            response = XsltDoc.class)
    StatisticStoreDoc read(@ApiParam("docRef") DocRef docRef);

    @PUT
    @Path("/update")
    @ApiOperation(
            value = "Update a statistic doc",
            response = StatisticStoreDoc.class)
    StatisticStoreDoc update(@ApiParam("statisticStoreDoc") StatisticStoreDoc statisticStoreDoc);
}
