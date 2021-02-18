package stroom.core.welcome;

import stroom.event.logging.api.StroomEventLoggingService;
import stroom.event.logging.api.StroomEventLoggingUtil;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.ui.config.shared.UiConfig;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;

import event.logging.Banner;
import event.logging.ComplexLoggedOutcome;
import event.logging.ViewEventAction;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Tag(name = "Welcome")
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
    @Operation(summary = "Get the configured HTML welcome message")
    public Welcome fetch() {
        return stroomEventLoggingServiceProvider.get().loggedResult(
                StroomEventLoggingUtil.buildTypeId(this, "fetch"),
                "Get the configured HTML welcome message",
                ViewEventAction.builder()
                        .addBanner(Banner.builder()
                                .build())
                        .build(),
                eventAction -> {
                    final String msg = uiConfigProvider.get().getWelcomeHtml();
                    final Welcome welcome = new Welcome(msg);

                    return ComplexLoggedOutcome.success(
                            welcome,
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

    public class Welcome {

        private final String html;

        public Welcome(final String html) {
            this.html = html;
        }

        public String getHtml() {
            return html;
        }
    }
}
