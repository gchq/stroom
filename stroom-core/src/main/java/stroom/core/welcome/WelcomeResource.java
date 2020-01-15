package stroom.core.welcome;

import io.swagger.annotations.Api;
import stroom.ui.config.shared.UiConfig;
import stroom.util.shared.RestResource;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Api(value = "welcome - /v1")
@Path("/welcome/v1")
@Produces(MediaType.APPLICATION_JSON)
public class WelcomeResource implements RestResource {
    private final UiConfig uiConfig;

    @Inject
    WelcomeResource(final UiConfig uiConfig) {
        this.uiConfig = uiConfig;
    }

    @GET
    @Path("/")
    public Response welcome() {
        Object response = new Object() {
            public String html = uiConfig.getWelcomeHtml();
        };
        return Response.ok(response).build();
    }
}
