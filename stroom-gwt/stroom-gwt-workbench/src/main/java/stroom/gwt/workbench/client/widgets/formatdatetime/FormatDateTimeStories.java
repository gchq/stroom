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

package stroom.gwt.workbench.client.widgets.formatdatetime;

import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.preferences.client.DateTimeFormatter;
import stroom.preferences.client.UserPreferencesManager;
import stroom.query.api.UserTimeZone;
import stroom.ui.config.shared.UserPreferences;

import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.LIElement;
import com.google.gwt.dom.client.UListElement;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Widget;

/// Stories for Stroom's [DateTimeFormatter], the GWT equivalent of the React port's
/// `formatDateTime(ms, pattern, zone)` function, matching `Widgets/formatDateTime` in the React
/// Storybook.
///
/// Stroom formats a time with the user's date/time pattern (a Java pattern, converted to a
/// moment.js one) and time zone, from their preferences. Each case uses its own formatter with
/// fixed preferences holding React's pattern and zone, so the results don't depend on the
/// browser's zone or the current time.
public final class FormatDateTimeStories {

    /// 1,700,000,000,000 ms = 2023-11-14T22:13:20.000Z.
    private static final long MS = 1_700_000_000_000L;

    private FormatDateTimeStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("Widgets/formatDateTime", FormatDateTimeStories.class)
                // The default pattern renders ISO with milliseconds, as Stroom's DateTimeFormatter does
                .story("Default", context -> render())
                .withPlay(play -> {
                    // The default pattern (yyyy-MM-dd'T'HH:mm:ss.SSSXX) in UTC: ISO with milliseconds
                    // and a Z
                    play.expect(play.getByTestId("default-utc")).toHaveTextContent("2023-11-14T22:13:20.000Z");
                    play.expect(play.getByTestId("plain-utc")).toHaveTextContent("2023-11-14 22:13:20");
                    // Etc/GMT-1 is UTC+1, so the hour advances.
                    // Differs from React: Stroom converts Java's `XXX` (an ISO offset, +01:00) to
                    // moment.js's `XXX` (the Unix time in seconds, three times), so the offset isn't
                    // shown; the time before it is the same as React's
                    play.expect(play.getByTestId("offset-plus1"))
                            .toHaveTextContent("2023-11-14T23:13:20" + "1700000000".repeat(3));
                    play.expect(play.getByTestId("offset-plus1").textContent()).not().toMatch("+01:00");
                    play.expect(play.getByTestId("month-name")).toHaveTextContent("14 Nov 2023");
                });
    }

    /// React's `Demo`: a `<ul>` with an `<li data-testid={label}>` holding each case's result.
    private static Widget render() {
        final UListElement list = Document.get().createULElement();
        addCase(list, "default-utc", UserPreferences.DEFAULT_DATE_TIME_PATTERN, UserTimeZone.utc());
        addCase(list, "plain-utc", "yyyy-MM-dd HH:mm:ss", UserTimeZone.utc());
        addCase(list, "offset-plus1", "yyyy-MM-dd'T'HH:mm:ssXXX", UserTimeZone.fromId("Etc/GMT-1"));
        addCase(list, "month-name", "d MMM yyyy", UserTimeZone.utc());

        final FlowPanel panel = new FlowPanel();
        panel.getElement().appendChild(list);
        return panel;
    }

    private static void addCase(final UListElement list,
                                final String label,
                                final String pattern,
                                final UserTimeZone timeZone) {
        final UserPreferences preferences = UserPreferences.builder()
                .dateTimePattern(pattern)
                .timeZone(timeZone)
                .build();
        final DateTimeFormatter formatter = new DateTimeFormatter(new FixedUserPreferencesManager(preferences));

        final LIElement item = Document.get().createLIElement();
        item.setAttribute("data-testid", label);
        item.setInnerText(formatter.format(MS));
        list.appendChild(item);
    }

    // --------------------------------------------------------------------------------


    /// Stroom's [UserPreferencesManager] holding fixed preferences, without fetching them from the
    /// server or applying them to the page (as [UserPreferencesManager#setCurrentPreferences]
    /// would, changing the page's theme and Stroom's client time zone).
    private static final class FixedUserPreferencesManager extends UserPreferencesManager {

        private final UserPreferences preferences;

        private FixedUserPreferencesManager(final UserPreferences preferences) {
            super(null, null);
            this.preferences = preferences;
        }

        @Override
        public UserPreferences getCurrentUserPreferences() {
            return preferences;
        }
    }
}
