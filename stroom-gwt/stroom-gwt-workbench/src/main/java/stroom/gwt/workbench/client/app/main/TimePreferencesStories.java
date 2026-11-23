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
import stroom.gwt.workbench.framework.client.play.TextMatch;
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
                    final Query customFormat = customFormat(play);
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
                })
                // A custom format Java can't use is never reported, so the screens never show it:
                // it is said under the field, and another change is reported with the format before
                // it, until it is put right. It used to be applied as it was committed
                .story("InvalidCustomFormatNotReported", context -> render())
                .withPlay(play -> {
                    final Query reported = play.getByTestId(REPORTED);
                    play.click(play.getByRole("checkbox", "Custom"));
                    play.waitFor(() -> play.expect(reported).toHaveTextContent(TextMatch.startingWith("1 ")));
                    final Query customFormat = customFormat(play);
                    play.clear(customFormat);
                    play.type(customFormat, "dd/bb/yyyy");
                    play.tab();
                    play.waitFor(() -> play.expect(customFormat).toHaveAttribute("aria-invalid", "true"));
                    play.expect(customFormat).toHaveAccessibleDescription(
                            TextMatch.containing("Unknown pattern letter 'b'"));
                    play.expect(reported).toHaveTextContent(TextMatch.startingWith("1 "));

                    play.click(selection(play, "userPreferencesTimeZone"));
                    play.click(play.screen().findByText("UTC", ".selection-popup .selection-item-cell__text"));
                    play.waitFor(() -> play.expect(selection(play, "userPreferencesTimeZone"))
                            .toHaveValue("UTC"));
                    play.waitFor(() -> play.expect(reported)
                            .toHaveTextContent(TextMatch.startingWith("2 " + FORMAT + " | UTC ")));
                    play.expect(customFormat).toHaveAttribute("aria-invalid", "true");

                    play.clear(customFormat);
                    play.type(customFormat, "dd/MM/yyyy");
                    play.tab();
                    play.waitFor(() -> play.expect(reported)
                            .toHaveTextContent(TextMatch.startingWith("3 dd/MM/yyyy | UTC ")));
                    play.expect(customFormat).not().toHaveAttribute("aria-invalid", "true");
                })
                // Choosing a standard format clears the custom format's error with its text, which
                // used to stay under the box showing the standard format
                .story("StandardFormatClearsError", context -> render())
                .withPlay(play -> {
                    play.click(play.getByRole("checkbox", "Custom"));
                    final Query customFormat = customFormat(play);
                    play.type(customFormat, "#");
                    play.tab();
                    play.waitFor(() -> play.expect(customFormat).toHaveAttribute("aria-invalid", "true"));
                    play.click(selection(play, "userPreferencesDateFormat"));
                    play.click(play.screen().findByText("dd/MM/yyyy", ".selection-popup .selection-item-cell__text"));
                    play.waitFor(() -> play.expect(customFormat).toHaveValue("dd/MM/yyyy"));
                    play.expect(customFormat).not().toHaveAttribute("aria-invalid", "true");
                    play.expect(play.queryByText("'#' is reserved")).toBeNull();
                })
                // A saved format that isn't offered is the user's own: Custom is ticked (it never was, and
                // the list showed the format as if it offered it). Unticking goes to a standard one
                .story("SavedCustomFormat", context -> render("dd-MM-yyyy"))
                .withPlay(play -> {
                    final Query custom = play.getByRole("checkbox", "Custom");
                    play.expect(custom).toBeChecked();
                    play.expect(customFormat(play)).toHaveValue("dd-MM-yyyy");
                    play.expect(customFormat(play)).toBeEnabled();
                    play.expect(selection(play, "userPreferencesDateFormat")).toHaveValue("");
                    play.click(custom);
                    play.waitFor(() -> play.expect(play.getByTestId(REPORTED))
                            .toHaveTextContent(TextMatch.startingWith("1 " + FORMAT + " | ")));
                    play.expect(selection(play, "userPreferencesDateFormat")).toHaveValue(FORMAT);
                })
                // A format saved before formats were checked is said to be wrong as it is shown, not
                // only once it is left
                .story("SavedInvalidCustomFormat", context -> render("pdd/MM/yyyy"))
                .withPlay(play -> {
                    final Query customFormat = customFormat(play);
                    play.expect(customFormat).toHaveValue("pdd/MM/yyyy");
                    play.expect(customFormat).toHaveAttribute("aria-invalid", "true");
                    play.expect(customFormat).toHaveAccessibleDescription(
                            TextMatch.containing("Too few 'p' letters to pad 'd' (2 at least)"));
                });
    }

    private static Query customFormat(final Play play) {
        return play.within(play.getByText("Custom Format", "label").closest(".form-group")).querySelector("input");
    }

    private static Query selection(final Play play, final String identity) {
        return play.querySelector("#" + identity + " .selection-box__input");
    }

    private static FlowPanel render() {
        return render(FORMAT);
    }

    private static FlowPanel render(final String format) {
        final TimePreferences view = new TimePreferences();
        view.read(UserPreferences.builder()
                .dateTimePattern(format)
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
