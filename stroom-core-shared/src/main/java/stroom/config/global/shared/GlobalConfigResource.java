package stroom.config.global.shared;

import stroom.ui.config.shared.UiConfig;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;
import stroom.util.shared.filter.FilterFieldDefinition;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.fusesource.restygwt.client.DirectRestService;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.Arrays;
import java.util.List;

@Api(tags = "Global Config")
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
    FilterFieldDefinition FIELD_DEF_EFFECTIVE_VALUE = FilterFieldDefinition.qualifiedField(
            "Effective Value", "value");
    FilterFieldDefinition FIELD_DEF_SOURCE = FilterFieldDefinition.qualifiedField("Source");
    FilterFieldDefinition FIELD_DEF_DESCRIPTION = FilterFieldDefinition.qualifiedField(
            "Description", "desc");

    List<FilterFieldDefinition> FIELD_DEFINITIONS = Arrays.asList(
            FIELD_DEF_NAME,
            FIELD_DEF_EFFECTIVE_VALUE,
            FIELD_DEF_SOURCE,
            FIELD_DEF_DESCRIPTION);

    @POST
    @Path(PROPERTIES_SUB_PATH)
    @ApiOperation(value = "List all properties matching the criteria on the current node.")
    ListConfigResponse list(final @ApiParam("criteria") GlobalConfigCriteria criteria);

    @POST
    @Path(NODE_PROPERTIES_SUB_PATH + NODE_NAME_PATH_PARAM)
    @ApiOperation(value = "List all properties matching the criteria on the requested node.")
    ListConfigResponse listByNode(
            final @PathParam("nodeName") String nodeName,
            final @ApiParam("criteria") GlobalConfigCriteria criteria);

    @GET
    @Path(PROPERTIES_SUB_PATH + PROP_NAME_PATH_PARAM)
    @ApiOperation(value = "Fetch a property by its name, e.g. 'stroom.path.home'")
    ConfigProperty getPropertyByName(final @PathParam("propertyName") String propertyName);

    @GET
    @Path(CLUSTER_PROPERTIES_SUB_PATH + PROP_NAME_PATH_PARAM + YAML_OVERRIDE_VALUE_SUB_PATH + NODE_NAME_PATH_PARAM)
    @ApiOperation(value = "Get the property value from the YAML configuration in the specified node.")
    OverrideValue<String> getYamlValueByNodeAndName(final @PathParam("propertyName") String propertyName,
                                                    final @PathParam("nodeName") String nodeName);
    @POST
    @ApiOperation(value = "Create a configuration property")
    ConfigProperty create(@ApiParam("configProperty") final ConfigProperty configProperty);

    @PUT
    @Path(CLUSTER_PROPERTIES_SUB_PATH + "/{propertyName}")
    @ApiOperation(value = "Update a configuration property")
    ConfigProperty update(final @PathParam("propertyName") String propertyName,
                          final @ApiParam("configProperty") ConfigProperty configProperty);

    @GET
    @Path(FETCH_UI_CONFIG_SUB_PATH)
    @ApiOperation(value = "Fetch the UI configuration")
    UiConfig fetchUiConfig();
}
