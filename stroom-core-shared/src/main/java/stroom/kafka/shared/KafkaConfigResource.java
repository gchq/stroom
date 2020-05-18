/*
 * Copyright 2020 Crown Copyright
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

package stroom.kafka.shared;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.fusesource.restygwt.client.DirectRestService;
import stroom.docref.DocRef;
import stroom.util.shared.ResourceGeneration;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Api(value = "kafkaConfig - /v1")
@Path("/kafkaConfig" + ResourcePaths.V1)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface KafkaConfigResource extends RestResource, DirectRestService {

    @POST
    @Path("/read")
    @ApiOperation(
            value = "Get a kafkaConfig doc",
            response = KafkaConfigDoc.class)
    KafkaConfigDoc read(@ApiParam("docRef") DocRef docRef);

    @PUT
    @Path("/update")
    @ApiOperation(
            value = "Update a kafkaConfig doc",
            response = KafkaConfigDoc.class)
    KafkaConfigDoc update(@ApiParam("updated") KafkaConfigDoc updated);

    @POST
    @Path("/download")
    @ApiOperation(
            value = "Download a kafkaConfig doc",
            response = ResourceGeneration.class)
    ResourceGeneration download(@ApiParam("docRef") DocRef docRef);
}
