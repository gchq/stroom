package stroom.config.global.shared;

import stroom.ui.config.shared.UiConfig;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;
import stroom.util.shared.filter.FilterFieldDefinition;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.fusesource.restygwt.client.DirectRestService;

import java.util.Arrays;
import java.util.List;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

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
            final @Parameter(description = "criteria", required = true) GlobalConfigCriteria criteria);

    @POST
    @Path(NODE_PROPERTIES_SUB_PATH + NODE_NAME_PATH_PARAM)
    @Operation(
            summary = "List all properties matching the criteria on the requested node.",
            operationId = "listConfigPropertiesByNode")
    ListConfigResponse listByNode(
            final @PathParam("nodeName") String nodeName,
            final @Parameter(description = "criteria", required = true) GlobalConfigCriteria criteria);

    @GET
    @Path(PROPERTIES_SUB_PATH + PROP_NAME_PATH_PARAM)
    @Operation(
            summary = "Fetch a property by its name, e.g. 'stroom.path.home'",
            operationId = "getConfigPropertyByName")
    ConfigProperty getPropertyByName(final @PathParam("propertyName") String propertyName);

    @GET
    @Path(CLUSTER_PROPERTIES_SUB_PATH + PROP_NAME_PATH_PARAM + YAML_OVERRIDE_VALUE_SUB_PATH + NODE_NAME_PATH_PARAM)
    @Operation(
            summary = "Get the property value from the YAML configuration in the specified node.",
            operationId = "getConfigYamlValueByNodeAndName")
    OverrideValue<String> getYamlValueByNodeAndName(final @PathParam("propertyName") String propertyName,
                                                    final @PathParam("nodeName") String nodeName);

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
    ConfigProperty update(final @PathParam("propertyName") String propertyName,
                          final @Parameter(description = "configProperty", required = true)
                                  ConfigProperty configProperty);

    @GET
    @Path(FETCH_UI_CONFIG_SUB_PATH)
    @Operation(
            summary = "Fetch the UI configuration",
            operationId = "fetchUiConfig")
    UiConfig fetchUiConfig();
}
