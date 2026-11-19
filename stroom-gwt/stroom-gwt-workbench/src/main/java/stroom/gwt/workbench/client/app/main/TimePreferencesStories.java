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

package stroom.gwt.workbench.client.app.main;

import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.preferences.client.component.TimePreferences;
import stroom.query.api.UserTimeZone;
import stroom.query.api.UserTimeZone.Use;
import stroom.ui.config.shared.UserPreferences;
import stroom.widget.text.client.Label;

import com.google.gwt.user.client.ui.FlowPanel;

/// The `App/Main/TimePreferences` stories: the user preferences' Date and Time tab on its own (the
/// User Preferences dialog's own stories are in `App/Main/AppShell`). Under it, what the tab last
/// reported as a change, written as the dialog writes it: `<count> <format> | <use> <id> <hours>`.
public final class TimePreferencesStories {

    // What the tab last reported, shown under it
    private static final String REPORTED = "time-preferences-reported";
    private static final String FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSXX";

    private TimePreferencesStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Main/TimePreferences", TimePreferencesStories.class)
                .layout(StoryLayout.PADDED)
                // Every field reports its change, so the screens show it before OK, as the format and
                // the kind of time zone always have. The time zone Id, the offset and the custom
                // format didn't, so they showed only once the dialog was closed with OK. The custom
                // format reports once it is left, not as it is typed, so no half-typed format is
                // applied to the screens
                .story("EveryFieldReportsItsChange", context -> render())
                .withPlay(play -> {
                    final Query reported = play.getByTestId(REPORTED);
                    play.expect(reported).toHaveTextContent("0");

                    // The list of ids is long, so filtered to the one wanted
                    play.click(selection(play, "userPreferencesTimeZoneId"));
                    final Query filter = play.screen()
                            .querySelector(".selection-list__filter .quick-filter__input");
                    play.waitFor(() -> play.expect(filter).toBeInTheDocument());
                    play.type(filter, "London");
                    play.click(play.screen().findByRole("option", "Europe/London"));
                    play.waitFor(() -> play.expect(reported)
                            .toHaveTextContent("1 " + FORMAT + " | Id Europe/London 0"));

                    play.click(play.getByRole("checkbox", "Custom"));
                    play.waitFor(() -> play.expect(reported)
                            .toHaveTextContent("2 " + FORMAT + " | Id Europe/London 0"));
                    final Query customFormat = play.within(play.getByText("Custom Format", "label")
                            .closest(".form-group")).querySelector("input");
                    play.type(customFormat, "x");
                    // Nothing yet: the format is still being typed
                    play.expect(reported).toHaveTextContent("2 " + FORMAT + " | Id Europe/London 0");
                    play.tab();
                    play.waitFor(() -> play.expect(reported)
                            .toHaveTextContent("3 " + FORMAT + "x | Id Europe/London 0"));

                    play.click(selection(play, "userPreferencesTimeZone"));
                    play.click(play.screen().findByText("Offset", ".selection-popup .selection-item-cell__text"));
                    play.waitFor(() -> play.expect(reported)
                            .toHaveTextContent("4 " + FORMAT + "x | Offset Europe/London 0"));
                    play.click(play.getByRole("spinbutton", "Hours"));
                    play.keyboard("{ArrowUp}");
                    play.waitFor(() -> play.expect(reported)
                            .toHaveTextContent("5 " + FORMAT + "x | Offset Europe/London 1"));
                });
    }

    private static Query selection(final Play play, final String identity) {
        return play.querySelector("#" + identity + " .selection-box__input");
    }

    private static FlowPanel render() {
        final TimePreferences view = new TimePreferences();
        view.read(UserPreferences.builder()
                .dateTimePattern(FORMAT)
                .timeZone(UserTimeZone.builder().use(Use.ID).id("UTC").build())
                .build());
        final Label reported = new Label("0");
        reported.getElement().setAttribute("data-testid", REPORTED);
        final int[] count = {0};
        view.setOnChange(() -> {
            count[0]++;
            reported.setText(count[0] + " " + written(view));
        });
        final FlowPanel panel = new FlowPanel();
        panel.add(view);
        panel.add(reported);
        return panel;
    }

    // The preferences the tab writes, as `<format> | <use> <id> <hours>`
    private static String written(final TimePreferences view) {
        final UserPreferences.Builder builder = UserPreferences.builder();
        view.write(builder);
        final UserPreferences preferences = builder.build();
        final UserTimeZone timeZone = preferences.getTimeZone();
        return preferences.getDateTimePattern() + " | " + timeZone.getUse() + " " + timeZone.getId() + " "
               + timeZone.getOffsetHours();
    }
}
