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

import stroom.data.client.presenter.CharacterNavigatorPresenter;
import stroom.data.client.presenter.CharacterRangeSelectionPresenter;
import stroom.data.client.presenter.SourcePresenter;
import stroom.data.client.view.CharacterNavigatorViewImpl;
import stroom.data.client.view.CharacterRangeSelectionViewImpl;
import stroom.data.client.view.ClassificationLabel;
import stroom.data.client.view.ClassificationWrapperViewImpl;
import stroom.data.client.view.SourceViewImpl;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.pipeline.shared.SourceLocation;
import stroom.pipeline.stepping.client.event.BeginPipelineSteppingEvent;
import stroom.security.shared.AppPermission;
import stroom.widget.progress.client.presenter.ProgressPresenter;
import stroom.widget.progress.client.view.ProgressViewImpl;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;
import com.google.web.bindery.event.shared.EventBus;

/// Stories for Stroom's [SourcePresenter] (the 'Source' view of a stream: title, classification,
/// progress bar, character navigator and the read-only editor).
///
/// The data is served by fixtures for `POST /data/v1/fetch`.
public final class SourcePresenterStories {

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

    private SourcePresenterStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("Widgets/Editors & Viewers/SourcePresenter", SourcePresenterStories.class)
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
                .story("FetchError", context -> render(context, ERROR_FIXTURES, false, false));
    }

    private static Widget render(final StoryContext context,
                                 final RestFixtures fixtures,
                                 final boolean canStep,
                                 final boolean steppingSource) {
        // Stroom shows the stepping button whenever the user has the stepping permission, so the
        // other stories' user doesn't have it
        final ScreenHarness harness = ScreenHarness.builder(context, fixtures)
                .appPermissions(canStep
                        ? new AppPermission[]{AppPermission.VIEW_DATA_PERMISSION, AppPermission.STEPPING_PERMISSION}
                        : new AppPermission[]{AppPermission.VIEW_DATA_PERMISSION})
                .build();
        final EventBus eventBus = harness.getEventBus();
        // Stroom's Menu (for the source's context menus) is the harness's

        final SourcePresenter presenter = sourcePresenter(harness);
        harness.unbindOnCleanUp(presenter);

        harness.fn(ON_BEGIN_STEPPING);
        harness.addRegistration(eventBus.addHandler(BeginPipelineSteppingEvent.getType(), event ->
                harness.spy(ON_BEGIN_STEPPING, event.getStepLocation().toString())));

        presenter.setSteppingSource(steppingSource);
        presenter.setSourceLocation(SourceLocation.builder(META_ID)
                .withPartIndex(0L)
                .withRecordIndex(0L)
                .build());

        // SourceViewImpl keeps a 300px area at the top for the stepping meta
        // list, so much of the view is below the frame's fold
        harness.add(EditorWidgets.frame(presenter.getWidget(), WIDTH, HEIGHT));
        return harness.asWidget();
    }

    /// Creates the presenter and its views, as GIN would.
    static SourcePresenter sourcePresenter(final ScreenHarness harness) {
        final EventBus eventBus = harness.getEventBus();
        final ProgressPresenter progressPresenter = new ProgressPresenter(
                eventBus, new ProgressViewImpl(GWT.create(ProgressViewImpl.Binder.class)));
        final CharacterNavigatorPresenter characterNavigatorPresenter = new CharacterNavigatorPresenter(
                eventBus,
                new ProgressPresenter(eventBus, new ProgressViewImpl(GWT.create(ProgressViewImpl.Binder.class))),
                new CharacterNavigatorViewImpl(eventBus, GWT.create(CharacterNavigatorViewImpl.Binder.class)),
                () -> new CharacterRangeSelectionPresenter(
                        eventBus,
                        new CharacterRangeSelectionViewImpl(
                                GWT.create(CharacterRangeSelectionViewImpl.Binder.class))));
        return new SourcePresenter(
                eventBus,
                new SourceViewImpl(GWT.create(SourceViewImpl.Binder.class)),
                new ClassificationWrapperViewImpl(
                        GWT.create(ClassificationWrapperViewImpl.Binder.class),
                        new ClassificationLabel(harness.getUiConfigCache())),
                progressPresenter,
                EditorWidgets.textPresenter(eventBus),
                characterNavigatorPresenter,
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
