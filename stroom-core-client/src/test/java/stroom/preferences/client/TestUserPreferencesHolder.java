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

import stroom.query.api.UserTimeZone.Use;
import stroom.ui.config.shared.UserPreferences;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestUserPreferencesHolder {

    private static final UserPreferences USER_PREFERENCES = UserPreferences.builder()
            .theme("Light")
            .dateTimePattern("yyyy")
            .build();

    @Test
    void testDefaultsBeforeLoad() {
        final UserPreferencesHolder holder = new UserPreferencesHolder();
        assertThat(holder.isLoaded()).isFalse();
        // Never null, and the fields readers use straight away are filled in
        final UserPreferences preferences = holder.get();
        assertThat(preferences).isSameAs(UserPreferencesHolder.DEFAULT_USER_PREFERENCES);
        assertThat(preferences.getTheme()).isEqualTo(UserPreferences.DEFAULT_THEME_NAME);
        assertThat(preferences.getDateTimePattern()).isEqualTo(UserPreferences.DEFAULT_DATE_TIME_PATTERN);
        assertThat(preferences.getTimeZone().getUse()).isEqualTo(Use.UTC);
        assertThat(preferences.getEditorKeyBindings()).isEqualTo(UserPreferences.DEFAULT_EDITOR_KEY_BINDINGS);
    }

    @Test
    void testSet() {
        final UserPreferencesHolder holder = new UserPreferencesHolder();
        holder.set(USER_PREFERENCES);
        assertThat(holder.isLoaded()).isTrue();
        assertThat(holder.get()).isSameAs(USER_PREFERENCES);
    }

    @Test
    void testSetNull() {
        final UserPreferencesHolder holder = new UserPreferencesHolder();
        assertThatThrownBy(() -> holder.set(null)).isInstanceOf(NullPointerException.class);
        assertThat(holder.isLoaded()).isFalse();
    }

    @Test
    void testWhenLoadedWaitsForTheUsersPreferences() {
        final UserPreferencesHolder holder = new UserPreferencesHolder();
        final List<UserPreferences> received = new ArrayList<>();
        holder.whenLoaded(received::add);
        holder.whenLoaded(received::add);
        // Never called with the defaults
        assertThat(received).isEmpty();

        holder.set(USER_PREFERENCES);
        assertThat(received).containsExactly(USER_PREFERENCES, USER_PREFERENCES);

        // Each waiter is only called once, not on later changes
        final UserPreferences changed = USER_PREFERENCES.copy().theme("Dark").build();
        holder.set(changed);
        assertThat(received).hasSize(2);
        assertThat(holder.get()).isSameAs(changed);
    }

    @Test
    void testWhenLoadedAfterLoad() {
        final UserPreferencesHolder holder = new UserPreferencesHolder();
        holder.set(USER_PREFERENCES);
        final List<UserPreferences> received = new ArrayList<>();
        holder.whenLoaded(received::add);
        assertThat(received).containsExactly(USER_PREFERENCES);
    }

    @Test
    void testWaiterThatWaitsAgain() {
        final UserPreferencesHolder holder = new UserPreferencesHolder();
        final List<UserPreferences> received = new ArrayList<>();
        holder.whenLoaded(preferences -> holder.whenLoaded(received::add));
        holder.set(USER_PREFERENCES);
        // Loaded by then, so the nested consumer is called at once
        assertThat(received).containsExactly(USER_PREFERENCES);
    }

    @Test
    void testWhenLoadedNull() {
        final UserPreferencesHolder holder = new UserPreferencesHolder();
        assertThatThrownBy(() -> holder.whenLoaded(null)).isInstanceOf(NullPointerException.class);
    }
}
