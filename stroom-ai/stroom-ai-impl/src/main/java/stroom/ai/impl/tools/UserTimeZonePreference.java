/*
 * Copyright 2026 Crown Copyright
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

package stroom.ai.impl.tools;

import stroom.config.global.shared.UserPreferencesResource;
import stroom.query.api.UserTimeZone;
import stroom.ui.config.shared.UserPreferences;
import stroom.util.guice.RestResourcesBinder.ResourceType;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.RestResource;

import com.google.inject.Binding;
import com.google.inject.ImplementedBy;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;

/// The current user's Stroom time zone preference (`UserPreferences.timeZone`), which the Stroom UI applies to
/// dashboards and queries. Read as the user the tool call runs as.
@ImplementedBy(UserTimeZonePreference.FromUserPreferences.class)
public interface UserTimeZonePreference {

    /// No preference: Stroom's default, UTC.
    UserTimeZonePreference NONE = Optional::empty;

    /// @return The current user's time zone preference, or empty if it can't be read.
    Optional<UserTimeZone> current();


    // --------------------------------------------------------------------------------


    /// Reads the preference with Stroom's user preferences resource, the one the UI fetches its preferences from,
    /// called directly (not over REST, so nothing is logged): Stroom's user preferences service gives the current
    /// user's preferences only through it.
    final class FromUserPreferences implements UserTimeZonePreference {

        private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(FromUserPreferences.class);
        private static final Key<Map<ResourceType, Provider<RestResource>>> RESOURCES = Key.get(
                new TypeLiteral<>() {
                });

        private final Injector injector;

        @Inject
        FromUserPreferences(final Injector injector) {
            this.injector = injector;
        }

        @Override
        public Optional<UserTimeZone> current() {
            try {
                final Binding<Map<ResourceType, Provider<RestResource>>> binding =
                        injector.getExistingBinding(RESOURCES);
                if (binding == null) {
                    return Optional.empty();
                }
                for (final Entry<ResourceType, Provider<RestResource>> entry
                        : binding.getProvider().get().entrySet()) {
                    if (UserPreferencesResource.class.isAssignableFrom(entry.getKey().getResourceClass())) {
                        final UserPreferences preferences =
                                ((UserPreferencesResource) entry.getValue().get()).fetch();
                        return Optional.ofNullable(NullSafe.get(preferences, UserPreferences::getTimeZone));
                    }
                }
            } catch (final RuntimeException e) {
                LOGGER.debug(() -> LogUtil.message("Unable to read the user's time zone preference: {}",
                        e.getMessage()), e);
            }
            return Optional.empty();
        }
    }
}
