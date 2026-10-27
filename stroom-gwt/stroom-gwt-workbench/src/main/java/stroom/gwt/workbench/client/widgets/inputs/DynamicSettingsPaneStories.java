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

package stroom.gwt.workbench.client.widgets.inputs;

import stroom.dashboard.client.vis.DynamicSettingsPane;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.client.app.screen.StroomDom;
import stroom.gwt.workbench.client.widgets.dateandtime.DateTimeWidgets;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;

import com.google.gwt.json.client.JSONArray;
import com.google.gwt.json.client.JSONParser;
import com.google.gwt.user.client.ui.Widget;

import java.util.Collections;

/// Stories for Stroom's [DynamicSettingsPane] (a tab of a visualisation's settings, its controls
/// made from the visualisation's JSON), with one control of each kind but a field.
public final class DynamicSettingsPaneStories {

    private static final String ON_CHANGE = "onChange";
    private static final String CONTROLS = "["
            + "{\"id\": \"title\", \"type\": \"text\", \"label\": \"Title\"},"
            + "{\"id\": \"colour\", \"type\": \"selection\", \"label\": \"Colour\", "
            + "\"values\": [\"Red\", \"Blue\"]},"
            + "{\"id\": \"count\", \"type\": \"number\", \"label\": \"Count\"},"
            + "{\"id\": \"from\", \"type\": \"date\", \"label\": \"From\"},"
            + "{\"id\": \"show\", \"type\": \"boolean\", \"label\": \"Show Legend\"}"
            + "]";
    private static final String SETTINGS = "{\"title\": \"Requests\", \"colour\": \"Blue\", \"count\": \"10\", "
                                           + "\"from\": \"2024-01-15T09:50:00.000Z\", \"show\": \"true\"}";

    private DynamicSettingsPaneStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("Widgets/Inputs/DynamicSettingsPane", DynamicSettingsPaneStories.class)
                .layout(StoryLayout.PADDED)
                // One control of each kind, filled from the settings
                .story("Controls", context -> render(context, ReadOnly.NO))
                .withPlay(play -> {
                    play.expect(play.getByLabelText("Title")).toHaveValue("Requests");
                    play.expect(play.getByLabelText("Colour")).toHaveValue("Blue");
                    play.expect(play.getByLabelText("Count")).toHaveValue("10");
                    play.expect(play.getByLabelText("From")).toHaveValue("2024-01-15T09:50:00.000Z");
                    play.expect(play.getByRole("checkbox")).toBeChecked();
                    // Reading the settings isn't a change; a change to a setting is told
                    play.expect(play.spy(ON_CHANGE)).not().toHaveBeenCalled();
                    play.type(play.getByLabelText("Title"), "!");
                    play.tab();
                    play.waitFor(() -> play.expect(play.spy(ON_CHANGE)).toHaveBeenCalled());
                })
                // The date's calendar opens the Set Date And Time dialog: the pane hands its date
                // boxes to its owner, whose controller attaches what opens it
                .story("DateOpensDialog", context -> render(context, ReadOnly.NO))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.click(play.querySelector(".date-time-box .icon-box__icon"));
                    final Play dialog = screen.within(screen.findByText("Set Date And Time", StroomDom.DIALOG_TITLE)
                            .closest(StroomDom.DIALOG));
                    play.click(dialog.getByRole("button", "Midday"));
                    play.click(dialog.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(screen.queryByText("Set Date And Time", StroomDom.DIALOG_TITLE))
                            .toBeNull());
                    play.expect("the date", () -> play.getByLabelText("From").value().get())
                            .toMatch("T12:00:00.000");
                    play.expect(play.spy(ON_CHANGE)).toHaveBeenCalled();
                })
                // Read only once filled: every control can be read but not changed, and the date's
                // dialog doesn't open
                .story("ReadOnly", context -> render(context, ReadOnly.AFTER_READING))
                .withPlay(DynamicSettingsPaneStories::expectReadOnly)
                // Read only before the controls are added: each is made read only as it is added
                .story("ReadOnlyBeforeControls", context -> render(context, ReadOnly.BEFORE_CONTROLS))
                .withPlay(DynamicSettingsPaneStories::expectReadOnly);
    }

    private static void expectReadOnly(final Play play) {
        play.expect(play.getByLabelText("Title")).toHaveAttribute("readonly");
        play.expect(play.getByLabelText("Colour")).toHaveAttribute("aria-readonly", "true");
        play.expect(play.getByLabelText("Count")).toHaveAttribute("readonly");
        play.expect(play.getByLabelText("From")).toHaveAttribute("readonly");
        play.expect(play.getByRole("checkbox")).toHaveAttribute("aria-readonly", "true");
        play.expect(play.querySelector(".date-time-box")).toHaveClass("icon-box--read-only");
        play.click(play.querySelector(".date-time-box .icon-box__icon"));
        play.sleep(200);
        play.expect(play.screen().queryByText("Set Date And Time", StroomDom.DIALOG_TITLE)).toBeNull();
    }

    private static Widget render(final StoryContext context, final ReadOnly readOnly) {
        final ScreenHarness harness = ScreenHarness.create(context, RestFixtures.none());
        final Spy onChange = context.fn(ON_CHANGE);
        final DynamicSettingsPane pane = new DynamicSettingsPane();
        // As its owner (BasicVisSettingsController) does
        pane.setOnDateTimeBoxAdded(box -> DateTimeWidgets.withPicker(harness, box));
        pane.setOnChange(onChange::call);
        pane.setReadOnly(readOnly == ReadOnly.BEFORE_CONTROLS);
        final JSONArray controls = JSONParser.parseStrict(CONTROLS).isArray();
        pane.addControls(controls, Collections.emptyMap());
        pane.read(JSONParser.parseStrict(SETTINGS).isObject());
        if (readOnly == ReadOnly.AFTER_READING) {
            pane.setReadOnly(true);
        }
        pane.getElement().getStyle().setProperty("width", "400px");
        harness.add(pane);
        return harness.asWidget();
    }


    // --------------------------------------------------------------------------------


    // When the pane is made read only, if at all
    private enum ReadOnly {
        NO,
        BEFORE_CONTROLS,
        AFTER_READING
    }
}
