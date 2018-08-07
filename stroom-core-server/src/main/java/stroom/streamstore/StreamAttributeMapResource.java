/*
 *
 *  * Copyright 2018 Crown Copyright
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *     http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package stroom.streamstore;

import com.codahale.metrics.health.HealthCheck;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.IdSet;
import stroom.entity.shared.PageRequest;
import stroom.entity.shared.PageResponse;
import stroom.entity.shared.Sort;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionTerm;
import stroom.security.Security;
import stroom.streamstore.shared.FindStreamAttributeMapCriteria;
import stroom.streamstore.shared.StreamAttributeMap;
import stroom.util.HasHealthCheck;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;

@Api(
        value = "stream attribute map - /v1",
        description = "Stream Attribute Map API")
@Path("/streamattributemap/v1")
@Produces(MediaType.APPLICATION_JSON)
public class StreamAttributeMapResource implements HasHealthCheck {

    private StreamAttributeMapService streamAttributeMapService;
    private Security security;

    @Inject
    public StreamAttributeMapResource(final StreamAttributeMapService streamAttributeMapService,
                                      final Security security){
        this.streamAttributeMapService = streamAttributeMapService;
        this.security = security;
    }

    @GET
    @Path("/")
    @Produces(MediaType.APPLICATION_JSON)
    public Response search(@QueryParam("pageOffset") Long pageOffset,
                           @QueryParam("pageSize") Integer pageSize){
        return security.secureResult(() -> {
            // Validate pagination params
            if((pageSize != null && pageOffset == null) || (pageSize == null && pageOffset != null)){
                return Response.status(Response.Status.BAD_REQUEST).entity("A pagination request requires both a pageSize and an offset").build();
            }

            //Convert pageOffset (i.e. page from index 0) to item offset.
            final long itemOffset = pageOffset * pageSize;

            // Configure default criteria
            FindStreamAttributeMapCriteria criteria = new FindStreamAttributeMapCriteria();
            criteria.setPageRequest(new PageRequest(itemOffset, pageSize));
            // TODO Replace with Set.of() when we move to 1.9
            criteria.setFetchSet(Sets.newHashSet("StreamType", "StreamProcessor", "Volume", "Feed", "Pipeline"));
            criteria.setSort(new Sort("Create Time", Sort.Direction.DESCENDING, false));
            ExpressionTerm expressionTerm = new ExpressionTerm("Status", ExpressionTerm.Condition.EQUALS, "Unlocked");
            ExpressionOperator expressionOperator = new ExpressionOperator(true, ExpressionOperator.Op.AND, expressionTerm);
            criteria.getFindStreamCriteria().setExpression(expressionOperator);

            BaseResultList<StreamAttributeMap> results = streamAttributeMapService.find(criteria);
            Object response = new Object() {
                public PageResponse pageResponse = results.getPageResponse();
                public List<StreamAttributeMap> streamAttributeMaps = results.getValues();
            };
            return Response.ok(response).build();
        });
    }

    @GET
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response search(@PathParam("id") Long id){
        return security.secureResult(() -> {
            // Configure default criteria
            FindStreamAttributeMapCriteria criteria = new FindStreamAttributeMapCriteria();
            // TODO Replace with Set.of() when we move to 1.9
            criteria.setFetchSet(Sets.newHashSet("StreamType", "StreamProcessor", "Volume", "Feed", "Pipeline"));
            criteria.setUseCache(false);
            IdSet idSet = new IdSet();
            idSet.add(id);
            criteria.getFindStreamCriteria().setSelectedIdSet(idSet);

            BaseResultList<StreamAttributeMap> results = streamAttributeMapService.find(criteria);
            if(results.size() == 0){
                return Response.status(Response.Status.NOT_FOUND).build();
            }
            return Response.ok(results.getFirst()).build();
        });
    }


    @Override
    public HealthCheck.Result getHealth() {
        return null;
    }
}
