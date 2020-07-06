package stroom.security.impl;

import stroom.security.shared.InvalidateSessionResource;
import stroom.security.shared.ValidateSessionResponse;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.fusesource.restygwt.client.DirectRestService;

import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

@Api(value = "stroomSession - /v1")
@Path("/stroomSession" + ResourcePaths.V1)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface StroomSessionResource extends RestResource, InvalidateSessionResource {
    @GET
    @Path("/noauth/validateSession")
    @ApiOperation(
            value = "Validate the current session, return a redirect Uri if invalid.",
            response = Boolean.class)
    ValidateSessionResponse validateSession(@Context @NotNull HttpServletRequest request,
                                            @QueryParam("redirect_uri") @NotNull String redirectUri);

    @GET
    @Path("invalidate")
    @ApiOperation(
            value = "Invalidate the current session",
            response = Boolean.class)
    Boolean invalidate();
}
