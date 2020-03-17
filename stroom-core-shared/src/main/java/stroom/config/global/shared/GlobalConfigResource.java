package stroom.config.global.shared;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.fusesource.restygwt.client.DirectRestService;
import stroom.ui.config.shared.UiConfig;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;

import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

@Api(value = "config - /v1")
@Path(GlobalConfigResource.BASE_PATH)
@Produces(MediaType.APPLICATION_JSON)
public interface GlobalConfigResource extends RestResource, DirectRestService {
    String BASE_PATH = "/config" + ResourcePaths.V1;
    String PROPERTIES_SUB_PATH = "/properties";
    String YAML_OVERRIDE_VALUE_SUB_PATH = "/yamlOverrideValue";
    String DB_OVERRIDE_VALUE_SUB_PATH = "/dbOverrideValue";
    String CLUSTER_PROPERTIES_SUB_PATH = "/clusterProperties";

    String PROP_NAME_PATH_PARAM = "/{propertyName}";
    String NODE_NAME_PATH_PARAM = "/{nodeName}";

    @GET
    @Path(PROPERTIES_SUB_PATH)
    @Produces(MediaType.APPLICATION_JSON)
    ListConfigResponse list(
        final @QueryParam("partialName") String partialName,
        final @DefaultValue ("0") @QueryParam("offset") long offset,
        final @QueryParam("size") Integer size);

    @GET
    @Path(NODE_NAME_PATH_PARAM + PROPERTIES_SUB_PATH)
    @Produces(MediaType.APPLICATION_JSON)
    ListConfigResponse listByNode(
        final @PathParam("nodeName") String nodeName,
        final @QueryParam("partialName") String partialName,
        final @DefaultValue ("0") @QueryParam("offset") long offset,
        final @QueryParam("size") Integer size);

    @GET
    @Path(PROPERTIES_SUB_PATH + PROP_NAME_PATH_PARAM)
    @Produces(MediaType.APPLICATION_JSON)
    ConfigProperty getPropertyByName(final @PathParam("propertyName") String propertyName);

//    @GET
//    @Path(PROPERTIES_SUB_PATH + PROP_NAME_PATH_PARAM + YAML_OVERRIDE_VALUE_SUB_PATH)
//    @Produces(MediaType.APPLICATION_JSON)
//    OverrideValue<String> getYamlValueByName(final @PathParam("propertyName") String propertyName);

    @GET
    @Path(CLUSTER_PROPERTIES_SUB_PATH + PROP_NAME_PATH_PARAM + YAML_OVERRIDE_VALUE_SUB_PATH + NODE_NAME_PATH_PARAM)
    @Produces(MediaType.APPLICATION_JSON)
    OverrideValue<String> getYamlValueByNodeAndName(final @PathParam("propertyName") String propertyName,
                                                    final @PathParam("nodeName") String nodeName);

//    @POST
//    @Path("/find")
//    @Consumes(MediaType.APPLICATION_JSON)
//    @Produces(MediaType.APPLICATION_JSON)
//    @ApiOperation(
//            value = "Get global config properties",
//            response = ResultPage.class)
//    ResultPage<ConfigProperty> find(FindGlobalConfigCriteria criteria);

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            value = "Update a ConfigProperty",
            response = ConfigProperty.class)
    ConfigProperty create(final ConfigProperty configProperty);

    @PUT
    @Path(CLUSTER_PROPERTIES_SUB_PATH + "/{propertyName}" + DB_OVERRIDE_VALUE_SUB_PATH)
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            value = "Update a ConfigProperty",
            response = ConfigProperty.class)
    ConfigProperty update(final @PathParam("propertyName") String propertyName,
                          final ConfigProperty configProperty);

    @GET
    @Path("/fetchUiConfig")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            value = "Get config property",
            response = UiConfig.class)
    UiConfig fetchUiConfig();
}
