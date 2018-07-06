
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

package stroom.streamstore.resource;

import com.codahale.metrics.health.HealthCheck;
import io.swagger.annotations.Api;
import org.apache.commons.lang.NotImplementedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.PageRequest;
import stroom.streamstore.StreamAttributeMapService;
import stroom.streamstore.shared.FindStreamAttributeMapCriteria;
import stroom.streamstore.shared.StreamAttributeMap;
import stroom.util.HasHealthCheck;

import javax.inject.Inject;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Api(
        value = "stream store - /v1",
        description = "Stroom Store API")
@Path("/streamstore/v1")
@Produces(MediaType.APPLICATION_JSON)
public class StreamStoreResource implements HasHealthCheck {
    private static final Logger LOGGER = LoggerFactory.getLogger(StreamStoreResource.class);

    private StreamAttributeMapService streamAttributeMapService;

    @Inject
    public StreamStoreResource(
            StreamAttributeMapService streamAttributeMapService) {
        this.streamAttributeMapService = streamAttributeMapService;
    }
    @GET
    @Path("/")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response fetch(
            @NotNull @QueryParam("offset") Long offset,
            @QueryParam("pageSize") Integer pageSize
            // TODO StreamAttributeMapServiceImpl does not support custom sorting yet, see line 117
            // @QueryParam("sortBy") String sortBy,
            // @QueryParam("sortDirection") String sortDirection,
            // TODO Filtering not yet supported
            //@QueryParam("filter") String filter) {
    ){
        // TODO: Authorisation
        // TODO: Validation

        FindStreamAttributeMapCriteria criteria = new FindStreamAttributeMapCriteria();
        criteria.setPageRequest(new PageRequest(offset, pageSize));
//        if(!Strings.isNullOrEmpty(sortBy) && !Strings.isNullOrEmpty(sortDirection)) {
//            criteria.setSort(new Sort(sortBy, Sort.Direction.valueOf(sortDirection), true));
//        }

        BaseResultList<StreamAttributeMap> result = streamAttributeMapService.find(criteria);
        List<Map<String, String>> mappedResults = mapToResponseType(result);
        return Response.ok(mappedResults).build();
    }

    private List<Map<String, String>> mapToResponseType(BaseResultList<StreamAttributeMap> streams) {
        // At the moment we're just taking the response from the find and just getting the simple map,
        // which is all we need. At some point we're going to want or need to be more specific about the data
        // we pull back and this probably won't be appropriate.
        return streams.stream().map(stream -> stream.asMap()).collect(Collectors.toList());
    }

    @Override
    public HealthCheck.Result getHealth() {
        throw new NotImplementedException("public HealthCheck.Result getHealth()");
    }

    @Override
    public HealthCheck getHealthCheck() {
        throw new NotImplementedException("public HealthCheck getHealthCheck()");
    }
}
