package stroom.config.global.shared;

import stroom.ui.config.shared.UserPreferences;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.fusesource.restygwt.client.DirectRestService;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Tag(name = "Preferences")
@Path(UserPreferencesResource.BASE_PATH)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface UserPreferencesResource extends RestResource, DirectRestService {

    String BASE_PATH = "/preferences" + ResourcePaths.V1;

    @GET
    @Operation(
            summary = "Fetch user preferences.",
            operationId = "fetchUserPreferences")
    UserPreferences fetch();

    @POST
    @Operation(
            summary = "Update user preferences",
            operationId = "updateUserPreferences")
    boolean update(UserPreferences userPreferences);

    @POST
    @Path("setDefaultUserPreferences")
    @Operation(
            summary = "Sets the default preferences for all users",
            operationId = "setDefaultUserPreferences")
    UserPreferences setDefaultUserPreferences(UserPreferences userPreferences);

    @POST
    @Path("resetToDefaultUserPreferences")
    @Operation(
            summary = "Resets preferences to the defaults",
            operationId = "resetToDefaultUserPreferences")
    UserPreferences resetToDefaultUserPreferences();
}
