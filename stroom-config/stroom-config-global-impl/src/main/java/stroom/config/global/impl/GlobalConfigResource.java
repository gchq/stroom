package stroom.config.global.impl;

import com.codahale.metrics.annotation.Timed;
import io.swagger.annotations.Api;
import org.fusesource.restygwt.client.DirectRestService;
import stroom.config.global.shared.ClusterConfigProperty;
import stroom.config.global.shared.ConfigProperty;
import stroom.util.guice.ResourcePaths;
import stroom.util.shared.RestResource;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.List;

// TODO This resource was added before realising that getting config values from other nodes
//   would need to use the existing cluster call code and not REST resources.
//   It needs to become a resource on the admin port
@Api(value = "config - /v1")
@Path(GlobalConfigResource.BASE_PATH)
@Produces(MediaType.APPLICATION_JSON)
public interface GlobalConfigResource extends RestResource, DirectRestService {

    String BASE_PATH = "/config/" + ResourcePaths.V1;
    String YAML_VALUE_SUB_PATH = "/yamlValue";
    String CLUSTER_PROPERTIES_SUB_PATH = "/clusterProperties";

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Timed
    List<ConfigProperty> getAllConfig();

    @GET
    @Path("/{propertyName}")
    @Produces(MediaType.APPLICATION_JSON)
    @Timed
    ConfigProperty getPropertyByName(final @PathParam("propertyName") String propertyName);

    @GET
    @Path(YAML_VALUE_SUB_PATH + "/{propertyName}")
    @Produces(MediaType.TEXT_PLAIN)
    @Timed
    String getYamlValueByName(final @PathParam("propertyName") String propertyName);

    @GET
    @Path(CLUSTER_PROPERTIES_SUB_PATH + "/{propertyName}")
    @Produces(MediaType.APPLICATION_JSON)
    @Timed
    ClusterConfigProperty getClusterPropertyByName(final @PathParam("propertyName") String propertyName);
}
