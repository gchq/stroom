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

package stroom.preferences.client;

import stroom.query.api.UserTimeZone;
import stroom.ui.config.shared.UserPreferences;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/// Holds the current user's preferences for [UserPreferencesManager].
///
/// The preferences are fetched asynchronously when the app starts, so until then this holds
/// defaults: [#get()] is never null, which suits code that needs a value straight away (e.g. to
/// format a date). Code that needs the user's real preferences, e.g. to change and save them,
/// uses [#whenLoaded(Consumer)] instead, which waits for them.
final class UserPreferencesHolder {

    /// The preferences used until the user's have been loaded: the defaults of
    /// [UserPreferences], with a UTC time zone, the default date/time pattern and the default
    /// theme filled in so that no reader sees those as null.
    static final UserPreferences DEFAULT_USER_PREFERENCES = UserPreferences.builder()
            .theme(UserPreferences.DEFAULT_THEME_NAME)
            .dateTimePattern(UserPreferences.DEFAULT_DATE_TIME_PATTERN)
            .timeZone(UserTimeZone.utc())
            .build();

    private final List<Consumer<UserPreferences>> waiters = new ArrayList<>();
    private UserPreferences userPreferences;

    /// @return The user's preferences if they have been loaded, otherwise
    /// [#DEFAULT_USER_PREFERENCES]. Never null.
    UserPreferences get() {
        return userPreferences != null
                ? userPreferences
                : DEFAULT_USER_PREFERENCES;
    }

    /// @return True if the user's preferences have been loaded.
    boolean isLoaded() {
        return userPreferences != null;
    }

    /// Sets the user's preferences, e.g. once they have been fetched or after the user changes
    /// them. The first time, this calls the consumers waiting in [#whenLoaded(Consumer)].
    ///
    /// @param userPreferences The user's preferences.
    void set(final UserPreferences userPreferences) {
        this.userPreferences = Objects.requireNonNull(userPreferences, "userPreferences");
        if (!waiters.isEmpty()) {
            // Copied first, as a consumer may wait again
            final List<Consumer<UserPreferences>> loadedWaiters = new ArrayList<>(waiters);
            waiters.clear();
            loadedWaiters.forEach(waiter -> waiter.accept(userPreferences));
        }
    }

    /// Calls the consumer with the user's preferences: at once if they have been loaded,
    /// otherwise once, when they are. Unlike [#get()] it never gives the defaults, so use it to
    /// change and save the preferences without losing the user's.
    ///
    /// @param consumer Called once with the user's preferences.
    void whenLoaded(final Consumer<UserPreferences> consumer) {
        Objects.requireNonNull(consumer, "consumer");
        if (userPreferences != null) {
            consumer.accept(userPreferences);
        } else {
            waiters.add(consumer);
        }
    }
}
