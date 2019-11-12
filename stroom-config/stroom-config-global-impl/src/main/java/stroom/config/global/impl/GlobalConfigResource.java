package stroom.config.global.impl;

import com.codahale.metrics.annotation.Timed;
import io.swagger.annotations.Api;
import stroom.util.shared.RestResource;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Api(value = "config - /v1")
@Path("/config/v1")
@Produces(MediaType.APPLICATION_JSON)
public interface GlobalConfigResource extends RestResource {

    @GET
    @Path("/")
    @Produces(MediaType.APPLICATION_JSON)
    @Timed
    Response getAllConfig();

    @GET
    @Path("/{propertyName}")
    @Produces(MediaType.APPLICATION_JSON)
    @Timed
    Response getPropertyByName(final @PathParam("propertyName") String propertyName);

    @GET
    @Path("/yamlValue/{propertyName}")
    @Produces(MediaType.TEXT_PLAIN)
    @Timed
    Response getYamlValueByName(final @PathParam("propertyName") String propertyName);

}
