package stroom.config.global.impl;

import stroom.config.global.shared.UserPreferencesResource;
import stroom.event.logging.api.StroomEventLoggingService;
import stroom.event.logging.api.StroomEventLoggingUtil;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.ui.config.shared.UserPreferences;

import event.logging.ComplexLoggedOutcome;
import event.logging.UpdateEventAction;

import java.util.Objects;
import jakarta.inject.Inject;
import jakarta.inject.Provider;

@AutoLogged
public class UserPreferencesResourceImpl implements UserPreferencesResource {

    private final Provider<UserPreferencesService> preferencesServiceProvider;
    private final Provider<StroomEventLoggingService> stroomEventLoggingServiceProvider;

    @Inject
    UserPreferencesResourceImpl(final Provider<UserPreferencesService> preferencesServiceProvider,
                                final Provider<StroomEventLoggingService> stroomEventLoggingServiceProvider) {

        this.preferencesServiceProvider = Objects.requireNonNull(preferencesServiceProvider);
        this.stroomEventLoggingServiceProvider = stroomEventLoggingServiceProvider;
    }

    @Override
    public UserPreferences fetch() {
        return preferencesServiceProvider.get().fetch();
    }

    @AutoLogged(OperationType.MANUALLY_LOGGED)
    @Override
    public boolean update(final UserPreferences userPreferences) {
        final UserPreferencesService userPreferencesService = preferencesServiceProvider.get();
        final UserPreferences beforePreferences = userPreferencesService.fetch();

        final StroomEventLoggingService stroomEventLoggingService = stroomEventLoggingServiceProvider.get();
        final boolean result = stroomEventLoggingService.loggedWorkBuilder()
                .withTypeId(StroomEventLoggingUtil.buildTypeId(this, "update"))
                .withDescription("Updating own user preferences")
                .withDefaultEventAction(UpdateEventAction.builder()
                        .withBefore(stroomEventLoggingService.convertToMulti(beforePreferences))
                        .withAfter(stroomEventLoggingService.convertToMulti(userPreferences))
                        .build())
                .withComplexLoggedResult(updateEventAction -> {
                    final boolean didUpdate = userPreferencesService.update(userPreferences) > 0;

                    if (didUpdate) {
                        return ComplexLoggedOutcome.success(didUpdate, updateEventAction);
                    } else {
                        return ComplexLoggedOutcome.failure(
                                didUpdate, updateEventAction, "No rows updated");
                    }
                })
                .getResultAndLog();

        return result;
    }

    @Override
    public UserPreferences setDefaultUserPreferences(final UserPreferences userPreferences) {
        return preferencesServiceProvider.get().setDefaultUserPreferences(userPreferences);
    }

    @AutoLogged(OperationType.UPDATE)
    @Override
    public UserPreferences resetToDefaultUserPreferences() {
        preferencesServiceProvider.get().delete();
        return preferencesServiceProvider.get().fetch();
    }
}
