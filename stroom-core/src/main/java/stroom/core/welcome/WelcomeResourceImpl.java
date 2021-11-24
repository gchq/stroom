package stroom.core.welcome;

import stroom.event.logging.api.StroomEventLoggingService;
import stroom.event.logging.api.StroomEventLoggingUtil;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.ui.config.shared.UiConfig;

import event.logging.Banner;
import event.logging.ComplexLoggedOutcome;
import event.logging.ViewEventAction;

import javax.inject.Inject;
import javax.inject.Provider;

public class WelcomeResourceImpl implements WelcomeResource {

    private final Provider<UiConfig> uiConfigProvider;
    private final Provider<StroomEventLoggingService> stroomEventLoggingServiceProvider;

    @Inject
    WelcomeResourceImpl(final Provider<UiConfig> uiConfigProvider,
                        final Provider<StroomEventLoggingService> stroomEventLoggingServiceProvider) {
        this.uiConfigProvider = uiConfigProvider;
        this.stroomEventLoggingServiceProvider = stroomEventLoggingServiceProvider;
    }

    @AutoLogged(value = OperationType.MANUALLY_LOGGED)
    @Override
    public Welcome fetch() {
        return stroomEventLoggingServiceProvider.get().loggedWorkBuilder()
                .withTypeId(StroomEventLoggingUtil.buildTypeId(this, "fetch"))
                .withDescription("Get the configured HTML welcome message")
                .withDefaultEventAction(ViewEventAction.builder()
                        .addBanner(Banner.builder()
                                .build())
                        .build())
                .withComplexLoggedResult(eventAction -> {
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
                })
                .getResultAndLog();
    }
}
