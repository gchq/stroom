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

package stroom.gwt.workbench.client.widgets.editorsandviewers;

import stroom.data.client.component.DataTextPane;
import stroom.data.client.component.DataTextPaneController;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.util.shared.DefaultLocation;
import stroom.util.shared.TextRange;

import com.google.gwt.user.client.ui.Widget;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditorMode;

import java.util.Collections;
import java.util.function.Consumer;

/// Stories for Stroom's [DataTextPaneController] (`stroom.data.client`, the editor that shows
/// stream data, with its stepping button).
public final class DataTextPaneStories {

    // Spy names
    private static final String ON_BEGIN_STEPPING = "onBeginStepping";

    private static final String WIDTH = "720px";
    private static final String HEIGHT = "380px";

    private DataTextPaneStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("Widgets/Editors & Viewers/DataTextPane", DataTextPaneStories.class)
                .layout(StoryLayout.CENTERED)
                // Read-only XML with line numbers and a highlighted range (the <EventSource> element)
                .story("XmlReadOnlyHighlighted", context -> render(context, AceEditorStories.SAMPLE_XML, pane -> {
                    pane.setMode(AceEditorMode.XML);
                    showLineNumbers(pane);
                    pane.setHighlights(Collections.singletonList(TextRange.of(
                            DefaultLocation.of(5, 5),
                            DefaultLocation.of(8, 19))));
                }))
                // Editable
                .story("Editable", context -> render(context, AceEditorStories.SAMPLE_XML, pane -> {
                    pane.setMode(AceEditorMode.XML);
                    pane.setReadOnly(false);
                    showLineNumbers(pane);
                }))
                // A properties-style log with the stepping button showing, which starts stepping
                .story("WithSteppingControls", context -> render(context, AceEditorStories.SAMPLE_TEXT, pane -> {
                    pane.setMode(AceEditorMode.PROPERTIES);
                    showLineNumbers(pane);
                    pane.setControlsVisible(true);
                }))
                .withPlay(play -> {
                    play.click(play.findByRole("button", "Enter Stepping Mode"));
                    play.expect(play.spy(ON_BEGIN_STEPPING)).toHaveBeenCalled();
                })
                // Error state: the editor is replaced by the error panel
                .story("ErrorState", context -> render(context, "", pane ->
                        pane.setErrorText("Unable to display source [1234:1:1]",
                                "The requested data could not be read.\nStream may have been deleted.")));
    }

    // The pane, owned by the story, showing the text, set up by the story and framed
    private static Widget render(final StoryContext context,
                                 final String text,
                                 final Consumer<DataTextPaneController> setUp) {
        final ScreenHarness harness = ScreenHarness.create(context, RestFixtures.none());
        final DataTextPaneController pane = harness.own(EditorWidgets.dataTextPane(harness.getEventBus())
                .attach(new DataTextPane()));
        pane.setText(text);
        final Spy onBeginStepping = context.fn(ON_BEGIN_STEPPING);
        pane.setOnBeginStepping(() -> onBeginStepping.call());
        setUp.accept(pane);
        harness.add(EditorWidgets.frame(pane.asWidget(), WIDTH, HEIGHT));
        return harness.asWidget();
    }

    /// Shows line numbers. The data text pane hides line numbers (and makes the option
    /// unavailable) as Stroom formats the data it shows.
    private static void showLineNumbers(final DataTextPaneController pane) {
        pane.getOptions().getLineNumbersOption().setAvailable(true);
        pane.getOptions().getLineNumbersOption().setOn(true);
    }
}
