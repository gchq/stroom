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

import stroom.data.client.component.CharacterNavigator;
import stroom.data.client.component.CharacterNavigatorController;
import stroom.data.client.component.CharacterRangeSelection;
import stroom.data.client.component.CharacterRangeSelectionController;
import stroom.data.client.component.ClassificationLabelController;
import stroom.data.client.component.SourceViewer;
import stroom.data.client.component.SourceViewerController;
import stroom.gwt.workbench.client.app.rest.RequestMatcher;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.client.app.screen.StroomDom;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.TextMatch;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.pipeline.shared.SourceLocation;
import stroom.pipeline.stepping.client.event.BeginPipelineSteppingEvent;
import stroom.security.shared.AppPermission;
import stroom.widget.progress.client.component.SourceProgressBar;

import com.google.gwt.user.client.ui.Widget;
import com.google.web.bindery.event.shared.EventBus;

/// Stories for Stroom's [SourceViewerController] (the 'Source' view of a stream: title, classification,
/// progress bar, character navigator and the read-only editor).
///
/// The data is served by fixtures for `POST /data/v1/fetch`.
public final class SourceStories {

    /// The name of the spy for the stepping button.
    static final String ON_BEGIN_STEPPING = "onBeginStepping";

    private static final long META_ID = 1234;
    private static final long TOTAL_CHARS = 250_000;
    private static final long WINDOW_CHARS = 80_000;
    private static final int LOADING_FOREVER_MILLIS = 24 * 60 * 60 * 1000;
    private static final int LOAD_DELAY_MILLIS = 250;

    private static final String WIDTH = "760px";
    private static final String HEIGHT = "440px";

    // A window of a (pretend) 250,000 character stream, starting at the
    // requested character
    private static final RestFixtures LOADED_FIXTURES = RestFixtures.builder()
            .post(DataFixtures.FETCH_PATH, request -> RestReply.json(loadedSource(request.getBody()))
                    .delayed(LOAD_DELAY_MILLIS))
            .build();

    // The fetch never resolves
    private static final RestFixtures LOADING_FIXTURES = RestFixtures.builder()
            .post(DataFixtures.FETCH_PATH, RestReply.json("{}").delayed(LOADING_FOREVER_MILLIS))
            .build();

    // A reply with errors (showErrors)
    private static final RestFixtures ERROR_FIXTURES = RestFixtures.builder()
            .post(DataFixtures.FETCH_PATH, RestReply.json("""
                    {
                      "type": "data",
                      "sourceLocation": {"metaId": 1234, "partIndex": 0, "recordIndex": 0},
                      "errors": ["Stream 1234 has been logically deleted.", "No data is available to display."]
                    }"""))
            .build();

    private SourceStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("Widgets/Editors & Viewers/SourcePresenter", SourceStories.class)
                .layout(StoryLayout.CENTERED)
                // A loaded non-segmented source: title, classification banner, click-to-seek progress
                // bar, character navigator and the read-only XML editor
                .story("LoadedSource", context -> render(context, LOADED_FIXTURES, false, false))
                // Stepping is allowed (the user has the stepping permission), so the stepping button
                // shows over the editor; clicking it begins stepping
                .story("SteppingEnabled", context -> render(context, LOADED_FIXTURES, true, false))
                // A stepping source: no View as Hex option.
                // The character navigator still shows
                .story("SteppingSource", context -> render(context, LOADED_FIXTURES, true, true))
                // Loading: the fetch never completes
                .story("Loading", context -> render(context, LOADING_FIXTURES, false, false))
                // Error state: the editor is replaced by the error panel
                .story("FetchError", context -> render(context, ERROR_FIXTURES, false, false))
                // An invalid range in Set Source Range says why and leaves the dialog usable: OK and
                // Cancel once stayed disabled after the message
                .story("InvalidRangeKeepsDialogUsable", context -> render(context, LOADED_FIXTURES, false, false,
                        true))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    final Query label = play.querySelector(".character-navigator__label");
                    play.waitFor(() -> play.expect(label).toBeVisible());
                    play.click(label);
                    final Play dialog = screen.within(screen.findByText("Set Source Range", StroomDom.DIALOG_TITLE)
                            .closest(StroomDom.DIALOG));
                    // The range opens as character offsets from 1; a range that ends where it starts
                    final Query to = dialog.querySelectorAll(".value-spinner input").nth(1);
                    play.clear(to);
                    play.type(to, "1");
                    play.click(dialog.getByRole("button", StroomDom.button("OK")));
                    play.click(screen.within(screen.findByText(TextMatch.containing("is equal to the to offset"))
                            .closest(StroomDom.DIALOG)).getByRole("button", StroomDom.button("Close")));
                    play.click(dialog.getByRole("button", StroomDom.button("Cancel")));
                    play.waitFor(() -> play.expect(screen.queryByText("Set Source Range", StroomDom.DIALOG_TITLE))
                            .toBeNull());
                })
                // A valid range in Set Source Range is fetched: here characters 1,001 to 2,000
                .story("SetRange", context -> render(context, LOADED_FIXTURES, false, false))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    final Query label = play.querySelector(".character-navigator__label");
                    play.waitFor(() -> play.expect(label).toBeVisible());
                    play.click(label);
                    final Play dialog = screen.within(screen.findByText("Set Source Range", StroomDom.DIALOG_TITLE)
                            .closest(StroomDom.DIALOG));
                    // From's offset, then To's
                    final Query from = dialog.getAllByRole("spinbutton", "Character offset").nth(0);
                    final Query to = dialog.getAllByRole("spinbutton", "Character offset").nth(1);
                    play.clear(to);
                    play.type(to, "2000");
                    play.clear(from);
                    play.type(from, "1001");
                    play.click(dialog.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(screen.queryByText("Set Source Range", StroomDom.DIALOG_TITLE))
                            .toBeNull());
                    // Zero based
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post(DataFixtures.FETCH_PATH)
                                    .withJsonBodyContaining("{\"sourceLocation\": {\"dataRange\": "
                                                            + "{\"charOffsetFrom\": 1000, \"charOffsetTo\": 1999}}}")
                                    .toSpyMatcher()));
                    play.expect(play.spy(ScreenHarness.ALERT_SPY)).not().toHaveBeenCalled();
                });
    }

    private static Widget render(final StoryContext context,
                                 final RestFixtures fixtures,
                                 final boolean canStep,
                                 final boolean steppingSource) {
        return render(context, fixtures, canStep, steppingSource, false);
    }

    // As above, with Stroom's real alerts if asked, e.g. to close one
    private static Widget render(final StoryContext context,
                                 final RestFixtures fixtures,
                                 final boolean canStep,
                                 final boolean steppingSource,
                                 final boolean realAlerts) {
        // Stroom shows the stepping button whenever the user has the stepping permission, so the
        // other stories' user doesn't have it
        final ScreenHarness.Builder builder = ScreenHarness.builder(context, fixtures)
                .appPermissions(canStep
                        ? new AppPermission[]{AppPermission.VIEW_DATA_PERMISSION, AppPermission.STEPPING_PERMISSION}
                        : new AppPermission[]{AppPermission.VIEW_DATA_PERMISSION});
        if (realAlerts) {
            builder.realAlerts();
        }
        final ScreenHarness harness = builder.build();
        final EventBus eventBus = harness.getEventBus();
        // Stroom's MenuManager (for the source's context menus) is the harness's

        final SourceViewerController controller = sourceController(harness);
        harness.own(controller);

        harness.fn(ON_BEGIN_STEPPING);
        harness.addRegistration(eventBus.addHandler(BeginPipelineSteppingEvent.getType(), event ->
                harness.spy(ON_BEGIN_STEPPING, event.getStepLocation().toString())));

        controller.setSteppingSource(steppingSource);
        controller.setSourceLocation(SourceLocation.builder(META_ID)
                .withPartIndex(0L)
                .withRecordIndex(0L)
                .build());

        // SourceViewer keeps a 300px area at the top for the stepping meta
        // list, so much of the view is below the frame's fold
        harness.add(EditorWidgets.frame(controller.asWidget(), WIDTH, HEIGHT));
        return harness.asWidget();
    }

    /// Creates the controller and its components, as GIN would.
    static SourceViewerController sourceController(final ScreenHarness harness) {
        final EventBus eventBus = harness.getEventBus();
        final SourceProgressBar sourceProgressBar = new SourceProgressBar();
        final CharacterNavigatorController characterNavigator = new CharacterNavigatorController(
                new CharacterNavigator(),
                () -> new CharacterRangeSelectionController(eventBus, new CharacterRangeSelection()));
        return new SourceViewerController(
                eventBus,
                new SourceViewer(),
                new ClassificationLabelController(harness.getUiConfigCache()),
                sourceProgressBar,
                EditorWidgets.dataTextPane(eventBus),
                characterNavigator,
                harness.getUiConfigCache(),
                harness.getRestFactory(),
                harness.getInjector().getClientSecurityContext());
    }

    /// A window of the stream from the requested character.
    private static String loadedSource(final String requestBody) {
        final long from = DataFixtures.getLong(requestBody, 0,
                "sourceLocation", "dataRange", "charOffsetFrom");
        final long to = Math.min(TOTAL_CHARS - 1, from + WINDOW_CHARS - 1);
        final int lineCount = AceEditorStories.SAMPLE_XML.split("\n").length;
        return "{"
               + "\"type\": \"data\", "
               + "\"data\": " + DataFixtures.quote(AceEditorStories.SAMPLE_XML) + ", "
               + "\"classification\": \"OFFICIAL-SENSITIVE\", "
               + "\"feedName\": \"TEST_FEED\", "
               + "\"dataType\": \"NON_SEGMENTED\", "
               + "\"displayMode\": \"TEXT\", "
               + "\"totalCharacterCount\": {\"count\": " + TOTAL_CHARS + ", \"exact\": true}, "
               + "\"totalBytes\": " + (TOTAL_CHARS + 12_000) + ", "
               + "\"sourceLocation\": {\"metaId\": " + META_ID + ", \"partIndex\": 0, \"recordIndex\": 0, "
               + "\"dataRange\": {\"charOffsetFrom\": " + from + ", \"charOffsetTo\": " + to + ", "
               + "\"locationFrom\": " + DataFixtures.location(1, 1) + ", "
               + "\"locationTo\": " + DataFixtures.location(lineCount, 1) + "}}"
               + "}";
    }
}
