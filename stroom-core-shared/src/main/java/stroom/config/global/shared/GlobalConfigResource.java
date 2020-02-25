package stroom.config.global.shared;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.fusesource.restygwt.client.DirectRestService;
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
    List<ConfigProperty> getAllConfig();

    @GET
    @Path(PROPERTIES_SUB_PATH + PROP_NAME_PATH_PARAM)
    @Produces(MediaType.APPLICATION_JSON)
    ConfigProperty getPropertyByName(final @PathParam("propertyName") String propertyName);

    @GET
    @Path(PROPERTIES_SUB_PATH + PROP_NAME_PATH_PARAM + YAML_OVERRIDE_VALUE_SUB_PATH)
    @Produces(MediaType.APPLICATION_JSON)
    OverrideValue<String> getYamlValueByName(final @PathParam("propertyName") String propertyName);

    @GET
    @Path(CLUSTER_PROPERTIES_SUB_PATH + PROP_NAME_PATH_PARAM + YAML_OVERRIDE_VALUE_SUB_PATH + NODE_NAME_PATH_PARAM)
    @Produces(MediaType.APPLICATION_JSON)
    OverrideValue<String> getYamlValueByNodeAndName(final @PathParam("propertyName") String propertyName,
                                                    final @PathParam("nodeName") String nodeName);

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

//    @GET
//    @Path(CLUSTER_PROPERTIES_SUB_PATH + "/{propertyName}")
//    @Produces(MediaType.APPLICATION_JSON)
//    @Timed
//    ClusterConfigProperty getClusterPropertyByName(final @PathParam("propertyName") String propertyName);
}
