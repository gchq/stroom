package stroom.core.welcome;

import stroom.event.logging.api.StroomEventLoggingService;
import stroom.event.logging.api.StroomEventLoggingUtil;
import stroom.ui.config.shared.UiConfig;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;

import event.logging.Banner;
import event.logging.ComplexLoggedOutcome;
import event.logging.ViewEventAction;
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
    private final Provider<StroomEventLoggingService> stroomEventLoggingServiceProvider;

    @Inject
    WelcomeResource(final Provider<UiConfig> uiConfigProvider,
                    final Provider<StroomEventLoggingService> stroomEventLoggingServiceProvider) {
        this.uiConfigProvider = uiConfigProvider;
        this.stroomEventLoggingServiceProvider = stroomEventLoggingServiceProvider;
    }

    @AutoLogged(value = OperationType.MANUALLY_LOGGED)
    @GET
    @ApiOperation(
            value = "Get the configured HTML welcome message",
            response = Object.class)
    public Response fetch() {

        return stroomEventLoggingServiceProvider.get().loggedResult(
                StroomEventLoggingUtil.buildTypeId(this, "fetch"),
                "Get the configured HTML welcome message",
                ViewEventAction.builder()
                        .addBanner(Banner.builder()
                                .build())
                        .build(),
                eventAction -> {

                    final String msg = uiConfigProvider.get().getWelcomeHtml();
                    final Response response = Response
                            .ok(new Object() {
                                public String html = msg;
                            })
                            .build();

                    return ComplexLoggedOutcome.success(
                            response,
                            ViewEventAction.builder()
                                    .addBanner(Banner.builder()
                                            .withMessage(msg)
                                            .build())
                                    .build()
                    );
                },
                null
        );
    }
}
