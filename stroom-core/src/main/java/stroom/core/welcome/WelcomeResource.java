package stroom.core.welcome;

import stroom.ui.config.shared.UiConfig;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Api(tags = "Welcome")
@Path("/welcome" + ResourcePaths.V1)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class WelcomeResource implements RestResource {
    private final Provider<UiConfig> uiConfigProvider;

    @Inject
    WelcomeResource(final Provider<UiConfig> uiConfigProvider) {
        this.uiConfigProvider = uiConfigProvider;
    }

    @GET
    @ApiOperation(
            value = "Get the configured HTML welcome message",
            response = Object.class)
    public Response welcome() {
        Object response = new Object() {
            public String html = uiConfigProvider.get().getWelcomeHtml();
        };
        return Response.ok(response)
                .build();
    }
}
