/*
 * Copyright 2016-2025 Crown Copyright
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

import stroom.ui.config.shared.ExtendedUiConfig;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;
import stroom.util.shared.filter.FilterFieldDefinition;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.fusesource.restygwt.client.DirectRestService;

import java.util.Arrays;
import java.util.List;

@Tag(name = "Global Config")
@Path(GlobalConfigResource.BASE_PATH)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface GlobalConfigResource extends RestResource, DirectRestService {

    String BASE_PATH = "/config" + ResourcePaths.V1;
    String PROPERTIES_SUB_PATH = "/properties";
    String NODE_PROPERTIES_SUB_PATH = "/nodeProperties";
    String YAML_OVERRIDE_VALUE_SUB_PATH = "/yamlOverrideValue";
    String DB_OVERRIDE_VALUE_SUB_PATH = "/dbOverrideValue";
    String CLUSTER_PROPERTIES_SUB_PATH = "/clusterProperties";
    String FETCH_UI_CONFIG_SUB_PATH = "/noauth/fetchUiConfig";
    String FETCH_EXTENDED_UI_CONFIG_SUB_PATH = "/noauth/fetchExtendedUiConfig";

    String PROP_NAME_PATH_PARAM = "/{propertyName}";
    String NODE_NAME_PATH_PARAM = "/{nodeName}";

    FilterFieldDefinition FIELD_DEF_NAME = FilterFieldDefinition.defaultField("Name");
    FilterFieldDefinition FIELD_DEF_VALUE = FilterFieldDefinition.qualifiedField(
            "Value", "value");
    FilterFieldDefinition FIELD_DEF_SOURCE = FilterFieldDefinition.qualifiedField("Source");
    FilterFieldDefinition FIELD_DEF_DESCRIPTION = FilterFieldDefinition.qualifiedField(
            "Description", "desc");

    List<FilterFieldDefinition> FIELD_DEFINITIONS = Arrays.asList(
            FIELD_DEF_NAME,
            FIELD_DEF_VALUE,
            FIELD_DEF_SOURCE,
            FIELD_DEF_DESCRIPTION);

    @POST
    @Path(PROPERTIES_SUB_PATH)
    @Operation(
            summary = "List all properties matching the criteria on the current node.",
            operationId = "listConfigProperties")
    ListConfigResponse list(
            @Parameter(description = "criteria", required = true) final GlobalConfigCriteria criteria);

    @POST
    @Path(NODE_PROPERTIES_SUB_PATH + NODE_NAME_PATH_PARAM)
    @Operation(
            summary = "List all properties matching the criteria on the requested node.",
            operationId = "listConfigPropertiesByNode")
    ListConfigResponse listByNode(
            @PathParam("nodeName") final String nodeName,
            @Parameter(description = "criteria", required = true) final GlobalConfigCriteria criteria);

    @GET
    @Path(PROPERTIES_SUB_PATH + PROP_NAME_PATH_PARAM)
    @Operation(
            summary = "Fetch a property by its name, e.g. 'stroom.path.home'",
            operationId = "getConfigPropertyByName")
    ConfigProperty getPropertyByName(@PathParam("propertyName") final String propertyName);

    @GET
    @Path(CLUSTER_PROPERTIES_SUB_PATH + PROP_NAME_PATH_PARAM + YAML_OVERRIDE_VALUE_SUB_PATH + NODE_NAME_PATH_PARAM)
    @Operation(
            summary = "Get the property value from the YAML configuration in the specified node.",
            operationId = "getConfigYamlValueByNodeAndName")
    OverrideValue<String> getYamlValueByNodeAndName(@PathParam("propertyName") final String propertyName,
                                                    @PathParam("nodeName") final String nodeName);

    @POST
    @Operation(
            summary = "Create a configuration property",
            operationId = "createConfigProperty")
    ConfigProperty create(
            @Parameter(description = "configProperty", required = true) final ConfigProperty configProperty);

    @PUT
    @Path(CLUSTER_PROPERTIES_SUB_PATH + "/{propertyName}")
    @Operation(
            summary = "Update a configuration property",
            operationId = "updateConfigProperty")
    ConfigProperty update(@PathParam("propertyName") final String propertyName,
                          @Parameter(description = "configProperty", required = true) final
                          ConfigProperty configProperty);

    @GET
    @Path(FETCH_EXTENDED_UI_CONFIG_SUB_PATH)
    @Operation(
            summary = "Fetch the extended UI configuration",
            operationId = "fetchExtendedUiConfig")
    ExtendedUiConfig fetchExtendedUiConfig();
}
