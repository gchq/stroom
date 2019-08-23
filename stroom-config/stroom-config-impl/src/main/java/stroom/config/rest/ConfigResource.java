package stroom.config.rest;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import stroom.config.global.api.ConfigProperty;
import stroom.util.RestResource;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Set;

@Api(value = "config - /v1")
@Path("/config/v1")
@Produces(MediaType.APPLICATION_JSON)
public interface ConfigResource extends RestResource {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/list")
    @ApiOperation(
            value = "Submit a request for a list of doc refs held by this service",
            response = Set.class)
    Response listProperties();

    @GET
    @Path("/global/{propertyName}")
    @Produces(MediaType.APPLICATION_JSON)
    Response fetch(@PathParam("propertyName") String name);

    @POST
    @Path("/{propertyName}")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    Response save(
            @PathParam("propertyName") String propertyName,
            ConfigProperty configProperty);

}
