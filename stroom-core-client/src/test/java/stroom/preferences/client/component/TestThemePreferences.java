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

package stroom.preferences.client.component;

import com.google.gwt.junit.GWTMockUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestThemePreferences {

    @BeforeEach
    void setUp() {
        // The tab is a component, made with GWT.create, which only works in a browser
        GWTMockUtilities.disarm();
    }

    @AfterEach
    void tearDown() {
        GWTMockUtilities.restore();
    }

    @Test
    void noSavedDensityShowsComfortable() {
        // Before, it showed "Default", which isn't an option, and saving the tab saved it
        assertThat(ThemePreferences.shownDensity(null)).isEqualTo("Comfortable");
    }

    @Test
    void defaultSavedBeforeShowsComfortable() {
        assertThat(ThemePreferences.shownDensity("Default")).isEqualTo("Comfortable");
    }

    @Test
    void savedOptionIsShown() {
        assertThat(ThemePreferences.shownDensity("Compact")).isEqualTo("Compact");
        assertThat(ThemePreferences.shownDensity("Comfortable")).isEqualTo("Comfortable");
    }
}
