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

package stroom.gwt.workbench.client.app.data;

import stroom.docref.DocRef;
import stroom.gwt.workbench.client.app.gin.processing.ProcessingScreenGinjector;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.client.app.screen.StroomDom;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.pipeline.client.component.SaveChangedDocumentsController;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;

import java.util.List;
import java.util.stream.Collectors;

/// The `App/Data/DocRefSelectionDialog` stories, showing Stroom's real [SaveChangedDocumentsController]
/// (the dialog that asks which changed documents to save), as `PipelinePlugin` shows it when a
/// pipeline is saved with other documents changed. Each document is ticked at first.
///
/// A spy records the names of the documents ticked when OK is pressed.
public final class SaveChangedDocumentsStories {

    /// The name of the spy recording the documents chosen.
    static final String ON_SELECTED = "onSelected";

    private static final String CAPTION = "Save Pipeline: Pipe A";
    private static final List<DocRef> DOC_REFS = List.of(
            new DocRef("Pipeline", "pipe-a-uuid", "Pipe A"),
            new DocRef("XSLT", "xslt-b-uuid", "XSLT B"));

    private SaveChangedDocumentsStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Data/DocRefSelectionDialog", SaveChangedDocumentsStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // Every document changed is listed, ticked
                .story("Default", SaveChangedDocumentsStories::render)
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.waitFor(() -> play.expect(screen.getByText(CAPTION)).toBeInTheDocument());
                    play.waitFor(() -> play.expect(screen.getByText("Pipe A")).toBeInTheDocument());
                    play.expect(screen.getByText("XSLT B")).toBeInTheDocument();
                    expectNoProblems(play);
                })
                // OK gives the documents still ticked
                .story("OkGivesTheDocumentsTicked", SaveChangedDocumentsStories::render)
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.waitFor(() -> play.expect(screen.getByText("XSLT B")).toBeInTheDocument());
                    // The tick box is a TickBoxCell div with no label
                    play.click(screen.within(screen.getByText("XSLT B").closest(StroomDom.GRID_ROW))
                            .querySelector(".tick-box"));
                    play.click(screen.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.spy(ON_SELECTED)).toHaveBeenCalledWith("Pipe A"));
                    play.waitFor(() -> play.expect(screen.queryByText(CAPTION)).toBeNull());
                    expectNoProblems(play);
                })
                // Cancel gives nothing
                .story("CancelGivesNothing", SaveChangedDocumentsStories::render)
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.waitFor(() -> play.expect(screen.getByText("Pipe A")).toBeInTheDocument());
                    play.click(screen.getByRole("button", StroomDom.button("Cancel")));
                    play.waitFor(() -> play.expect(screen.queryByText(CAPTION)).toBeNull());
                    play.expect(play.spy(ON_SELECTED)).not().toHaveBeenCalled();
                    expectNoProblems(play);
                });
    }

    private static void expectNoProblems(final Play play) {
        play.expect(play.spy(ScreenHarness.ALERT_SPY)).not().toHaveBeenCalled();
        play.expect(play.spy(ScreenHarness.UNHANDLED_REQUEST_SPY)).not().toHaveBeenCalled();
    }

    private static Widget render(final StoryContext context) {
        final ProcessingScreenGinjector injector = GWT.create(ProcessingScreenGinjector.class);
        final ScreenHarness harness = ScreenHarness.builder(context, RestFixtures.none())
                .injector(injector)
                .build();
        final Spy onSelected = harness.fn(ON_SELECTED);
        injector.getSaveChangedDocumentsController().show(harness.getAppRoot(), CAPTION, DOC_REFS, docRefs ->
                onSelected.call(docRefs.stream()
                        .map(DocRef::getName)
                        .collect(Collectors.joining(", "))));
        return harness.asWidget();
    }
}
