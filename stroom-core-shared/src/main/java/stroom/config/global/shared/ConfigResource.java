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

package stroom.config.global.shared;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.fusesource.restygwt.client.DirectRestService;
import stroom.ui.config.shared.UiConfig;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.List;

@Api(value = "config - /v1")
@Path(ConfigResource.BASE_PATH)
@Produces(MediaType.APPLICATION_JSON)
public interface ConfigResource extends RestResource, DirectRestService {
    String BASE_PATH = "/config/" + ResourcePaths.V1;
    String YAML_VALUE_SUB_PATH = "yamlValue";

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    List<ConfigProperty> getAllConfig();

    @GET
    @Path("/{propertyName}")
    @Produces(MediaType.APPLICATION_JSON)
    ConfigProperty getPropertyByName(final @PathParam("propertyName") String propertyName);

    @GET
    @Path("/" + YAML_VALUE_SUB_PATH + "/{propertyName}")
    @Produces(MediaType.TEXT_PLAIN)
    String getYamlValueByName(final @PathParam("propertyName") String propertyName);

    @POST
    @Path("/find")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            value = "Get global config properties",
            response = ConfigPropertyResultPage.class)
    ConfigPropertyResultPage find(FindGlobalConfigCriteria criteria);

    @GET
    @Path("/read/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            value = "Get config property",
            response = ConfigProperty.class)
    ConfigProperty read(@PathParam("id") Integer id);

    @PUT
    @Path("/update")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            value = "Update a config property",
            response = ConfigProperty.class)
    ConfigProperty update(ConfigProperty doc);

    @GET
    @Path("/fetchUiConfig")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            value = "Get config property",
            response = UiConfig.class)
    UiConfig fetchUiConfig();
}