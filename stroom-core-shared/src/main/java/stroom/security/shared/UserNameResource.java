package stroom.security.shared;

import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;
import stroom.util.shared.ResultPage;
import stroom.util.shared.UserName;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.fusesource.restygwt.client.DirectRestService;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Tag(name = "Authorisation")
@Path("/userNames" + ResourcePaths.V1)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface UserNameResource extends RestResource, DirectRestService {

    @POST
    @Path("/find")
    @Operation(
            summary = "Find the user names matching the supplied criteria",
            operationId = "findUserNames")
    ResultPage<UserName> find(@Parameter(description = "criteria", required = true) FindUserNameCriteria criteria);
}
