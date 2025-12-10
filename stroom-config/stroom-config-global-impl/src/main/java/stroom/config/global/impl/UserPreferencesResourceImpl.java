/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.config.global.impl;

import stroom.config.global.shared.UserPreferencesResource;
import stroom.event.logging.api.StroomEventLoggingService;
import stroom.event.logging.api.StroomEventLoggingUtil;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.ui.config.shared.UserPreferences;

import event.logging.ComplexLoggedOutcome;
import event.logging.UpdateEventAction;
import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.Objects;

@AutoLogged
public class UserPreferencesResourceImpl implements UserPreferencesResource {

    private final Provider<UserPreferencesServiceImpl> preferencesServiceProvider;
    private final Provider<StroomEventLoggingService> stroomEventLoggingServiceProvider;

    @Inject
    UserPreferencesResourceImpl(final Provider<UserPreferencesServiceImpl> preferencesServiceProvider,
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
        final UserPreferencesServiceImpl userPreferencesService = preferencesServiceProvider.get();
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
