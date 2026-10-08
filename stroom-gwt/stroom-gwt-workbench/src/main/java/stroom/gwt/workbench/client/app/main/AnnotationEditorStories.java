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

import stroom.annotation.client.FindAnnotationPresenter;
import stroom.annotation.client.ShowFindAnnotationEvent;
import stroom.gwt.workbench.client.app.gin.content.ContentScreenGinjector;
import stroom.gwt.workbench.client.app.rest.JsonValues;
import stroom.gwt.workbench.client.app.rest.RecordedRequest;
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

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;

import java.util.Map;

/// Stories matching `App/Main/AnnotationEditor` in the React Storybook, showing Stroom's real
/// [AnnotationPresenter] (an annotation's tab: the editor with its history, settings and choosers,
/// and the Events, Link To/From, Documentation and Permissions tabs) with fake REST replies.
///
/// The React story's `AnnotationApi`/`AnnotationTagApi` seams become routes for Stroom's
/// `AnnotationResource` (`/annotation/v1`, see [AnnotationFixtures]); its recorder becomes checks on
/// the request spy (`POST /change` with the change's JSON type, e.g. `title`, `setTag`,
/// `linkAnnotations`). The tab is opened as `AnnotationEditSupport` does for `EditAnnotationEvent`:
/// the annotation is fetched (`GET ?annotationId=42`), read and shown.
public final class AnnotationEditorStories {

    private static final String CHANGE_PATH = "/annotation/v1/change";
    // FindAnnotationPresenter's warning for OK with no annotation selected
    private static final String NOTHING_SELECTED = "No annotation has been selected";

    private static final String ENTRIES = "["
            + AnnotationFixtures.entry(1, "STATUS", "Alice", 1700000000000L, "Open", null)
            + ", "
            + AnnotationFixtures.entry(2, "COMMENT", "Alice", 1700000100000L, "Looking into it.", null)
            + "]";

    private AnnotationEditorStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Main/AnnotationEditor", AnnotationEditorStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // Loads the annotation; editing the title (blur) saves it; the predefined comment
                // chooser appends the tag's text to the comment
                .story("Editor", context -> render(context, fixtures(ENTRIES).build()))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.findByDisplayValue("Investigate alert");
                    final Query title = play.querySelector(".annotationTitleTextBox");
                    // The history renders the entries.
                    // Differs from React: GWT's history has no 'History' heading
                    play.findByText("Looking into it.");
                    play.clear(title);
                    play.type(title, "Investigate CPU alert");
                    play.tab();
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post(CHANGE_PATH)
                                    .withJsonBodyContaining("{\"change\": {\"type\": \"title\", "
                                            + "\"title\": \"Investigate CPU alert\"}}")
                                    .toSpyMatcher()));
                    // Differs from React: GWT's chooser button reads 'Choose Comment' and the comment
                    // box's label 'Add a comment'
                    play.click(play.getByRole("button", StroomDom.button("Choose Comment")));
                    play.click(screen.findByText("Escalate"));
                    play.waitFor(() -> play.expect(play.querySelector("textarea.annotationComment").value())
                            .toMatch(TextMatch.containing("Escalating to on-call.")));
                    ContentStorySupport.expectNoProblems(play);
                })
                // Link To tab: "Add Annotation Link" opens a chooser; OK links the chosen annotation.
                // The annotation found has who created it and when, which Stroom's list cell shows
                .story("LinkAnnotation", context -> render(context, AnnotationFixtures.editorRoutes(
                        RestFixtures.builder(), ENTRIES, "[]", AnnotationFixtures.page(
                                "{\"type\": \"Annotation\", \"uuid\": \"ann-99\", \"id\": 99, "
                                        + "\"name\": \"Related alert\", \"createUser\": \"admin\", "
                                        + "\"createTimeMs\": 1700000000000}")).build()))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.findByDisplayValue("Investigate alert");
                    play.click(tab(play, "Link To"));
                    play.click(play.findByTitle("Add Annotation Link"));
                    play.waitFor(() -> play.expect(screen.getByText("Choose Annotation")).toBeInTheDocument());
                    final Play chooser = screen.within(screen.getByText("Choose Annotation").closest(StroomDom.DIALOG));
                    // Differs from React: GWT doesn't select the first annotation found (its list only
                    // selects the first result when the filter changes), so OK with nothing selected
                    // warns and keeps the chooser open; the annotation is then selected
                    play.expect(chooser.findByText("Related alert").closest("tr"))
                            .not().toHaveClass("cellTableSelectedRow");
                    play.click(chooser.getByRole("button", StroomDom.button("OK")));
                    final Play warning = screen.within(screen.findByText(NOTHING_SELECTED).closest(StroomDom.DIALOG));
                    play.expect(play.spy(ScreenHarness.ALERT_SPY)).toHaveBeenCalledWith("WARN: " + NOTHING_SELECTED);
                    play.click(warning.getByRole("button", StroomDom.button("Close")));
                    play.waitFor(() -> play.expect(screen.queryByText(NOTHING_SELECTED)).toBeNull());
                    play.expect(screen.getByText("Choose Annotation", StroomDom.DIALOG_TITLE)).toBeInTheDocument();
                    play.expect(play.spy(ScreenHarness.REQUEST_SPY)).not().toHaveBeenCalledWith(
                            RequestMatcher.post(CHANGE_PATH).toSpyMatcher());
                    play.click(chooser.getByText("Related alert"));
                    play.click(chooser.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post(CHANGE_PATH)
                                    .withJsonBodyContaining("{\"change\": {\"type\": \"linkAnnotations\", "
                                            + "\"annotations\": [99]}}")
                                    .toSpyMatcher()));
                    play.waitFor(() -> play.expect(screen.queryByText("Choose Annotation", StroomDom.DIALOG_TITLE))
                            .toBeNull());
                    // The warning is the only alert
                    play.expect(play.spy(ScreenHarness.ALERT_SPY)).toHaveBeenCalledTimes(1);
                    play.expect(play.spy(ScreenHarness.UNHANDLED_REQUEST_SPY)).not().toHaveBeenCalled();
                })
                // Events tab: "Add Event Link" asks for streamId:eventId and links it
                .story("AddEventLink", context -> render(context, fixtures(ENTRIES).build()))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.findByDisplayValue("Investigate alert");
                    play.click(tab(play, "Events"));
                    play.click(play.findByTitle("Add Event Link"));
                    final Play dialog = screen.within(screen.findByText(TextMatch.containing("Link An Event"))
                            .closest(StroomDom.DIALOG));
                    play.type(dialog.getByRole("textbox"), "1234:5");
                    play.click(dialog.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post(CHANGE_PATH)
                                    .withJsonBodyContaining("{\"change\": {\"type\": \"linkEvents\", "
                                            + "\"events\": [{\"streamId\": 1234, \"eventId\": 5}]}}")
                                    .toSpyMatcher()));
                    ContentStorySupport.expectNoProblems(play);
                })
                // Events tab preview: the first linked event is selected and its data shown (record
                // index = event id - 1); selecting another shows its data
                .story("EventsPreview", context -> render(context, AnnotationFixtures.editorRoutes(
                        RestFixtures.builder()
                                .get("/meta/v1/*", AnnotationEditorStories::meta)
                                .post("/data/v1/fetch", AnnotationEditorStories::fetchData)
                                .get("/data/v1/*", RestReply.json("[null]")),
                        ENTRIES,
                        "[{\"streamId\": 1001, \"eventId\": 5}, {\"streamId\": 2002, \"eventId\": 9}]",
                        AnnotationFixtures.page()).build()))
                .withPlay(play -> {
                    play.findByDisplayValue("Investigate alert");
                    play.click(tab(play, "Events"));
                    play.findByText("1001:5");
                    play.expect(play.getByText("2002:9")).toBeInTheDocument();
                    play.waitFor(5000, () -> play.expect(play.getAllByText(
                                    TextMatch.containing("source for stream 1001 record 4")).count())
                            .toBeGreaterThanOrEqual(1));
                    play.click(play.getByText("2002:9"));
                    play.waitFor(5000, () -> play.expect(play.getAllByText(
                                    TextMatch.containing("source for stream 2002 record 8")).count())
                            .toBeGreaterThanOrEqual(1));
                    ContentStorySupport.expectNoProblems(play);
                })
                // An entry's menu edits a comment (fetch, dialog, change) and deletes an entry
                .story("EntryActions", context -> render(context, fixtures(ENTRIES).build()))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.findByText("Looking into it.");
                    // Differs from React: GWT has no 'Entry actions' buttons; pressing an entry's
                    // header (here the comment's) shows its menu
                    play.fireEvent().mouseDown(play.querySelectorAll(".annotationHistoryCommentHeader").nth(0));
                    play.click(screen.findByText("Edit Entry"));
                    final Play dialog = screen.within(screen.findByText("Edit Comment").closest(StroomDom.DIALOG));
                    final Query text = dialog.getByRole("textbox");
                    play.clear(text);
                    play.type(text, "Updated comment");
                    play.click(dialog.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post("/annotation/v1/changeAnnotationEntry")
                                    .withJsonBodyContaining("{\"data\": \"Updated comment\"}")
                                    .toSpyMatcher()));
                    // Delete the STATUS entry: confirm, then deleted
                    play.fireEvent().mouseDown(play.querySelectorAll("[entryType]").nth(0));
                    play.click(screen.findByText("Delete Entry"));
                    final Query confirm = screen.findByText(TextMatch.containingIgnoreCase("delete this entry"));
                    play.click(screen.within(confirm.closest(StroomDom.DIALOG))
                            .getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post("/annotation/v1/deleteAnnotationEntry").toSpyMatcher()));
                    ContentStorySupport.expectNoProblems(play);
                })
                // Consecutive entries of one group collapse into a header that expands on click
                .story("GroupedHistory", context -> render(context, fixtures("["
                        + AnnotationFixtures.entry(10, "ADD_LABEL", "Alice", 1700000000000L, "P1", null) + ", "
                        + AnnotationFixtures.entry(11, "ADD_LABEL", "Alice", 1700000010000L, "Bug", null) + ", "
                        + AnnotationFixtures.entry(12, "REMOVE_LABEL", "Alice", 1700000020000L, "P1", null)
                        + "]").build()))
                .withPlay(play -> {
                    final Query header = play.findByText("changed 3 labels");
                    play.expect(play.queryByText(TextMatch.containing("Bug"))).toBeNull();
                    play.click(header);
                    play.findByText(TextMatch.containing("Bug"));
                    ContentStorySupport.expectNoProblems(play);
                })
                // A change with a previous value shows old → new (struck through → inserted); without
                // one it reads "set the ..."
                .story("HistoryRichProse", context -> render(context, fixtures("["
                        + AnnotationFixtures.entry(1, "STATUS", "Alice", 1700000000000L, "Closed", "Open") + ", "
                        + AnnotationFixtures.entry(2, "TITLE", "Alice", 1700000100000L, "First title", null)
                        + "]").build()))
                .withPlay(play -> {
                    play.findByDisplayValue("Investigate alert");
                    play.waitFor(() -> play.expect(play.querySelector("del")).toBeInTheDocument());
                    play.expect(play.querySelector("del").textContent()).toBe("Open");
                    play.expect(play.querySelector("ins").textContent()).toBe("Closed");
                    play.expect(play.getByText(TextMatch.containing("changed the status"))).toBeInTheDocument();
                    play.expect(play.getByText(TextMatch.containing("set the title"))).toBeInTheDocument();
                    ContentStorySupport.expectNoProblems(play);
                })
                // Adding a label sends an addTag change; the Documentation and Permissions tabs exist
                .story("LabelsAndTabs", context -> render(context, fixtures(ENTRIES).build()))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.findByDisplayValue("Investigate alert");
                    // Click the Labels block to open its chooser, then tick P1.
                    // Differs from React: GWT's label chooser is a multi-select list (no '+ add')
                    play.click(play.getByText("Labels"));
                    play.click(screen.findByText("P1"));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post(CHANGE_PATH)
                                    .withJsonBodyContaining("{\"change\": {\"type\": \"addTag\", "
                                            + "\"tag\": {\"name\": \"P1\"}}}")
                                    .toSpyMatcher()));
                    play.expect(tab(play, "Documentation")).toBeInTheDocument();
                    play.expect(tab(play, "Permissions")).toBeInTheDocument();
                    ContentStorySupport.expectNoProblems(play);
                })
                // Read only (no Edit permission): everything can be read, nothing changed (the
                // annotation once ignored read only, and each change went straight to the server)
                .story("ReadOnly", context -> render(context, fixtures(ENTRIES).build(), true))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    final Query title = play.findByDisplayValue("Investigate alert");
                    play.expect(title).toHaveAttribute("readonly");
                    play.click(play.getByText("Status"));
                    play.expect(screen.queryByText("Closed")).toBeNull();
                    play.expect(play.getByRole("button", StroomDom.button("Comment"))).toBeDisabled();
                    play.expect(play.getByRole("button", StroomDom.button("Delete Annotation"))).toBeDisabled();
                    play.expect(play.queryByText("Assign yourself")).toBeNull();
                    play.expect(play.getByText("Read only", ".docTab-readOnlyNote")).toBeVisible();
                    // The status is at the end of the tab bar's row, not on the toolbar, and says why
                    // the annotation is read only, in a tooltip and to screen readers
                    final Query status = play.querySelector(".linkTabPanelViewImpl > .docTab-readOnlyStatus");
                    play.expect(status).toHaveAttribute("title", "You don't have permission to change this");
                    play.expect(status).toHaveTextContent(
                            TextMatch.containing("You don't have permission to change this."));
                    play.expect(play.spy(ScreenHarness.REQUEST_SPY)).not().toHaveBeenCalledWith(
                            RequestMatcher.post(CHANGE_PATH).toSpyMatcher());
                    ContentStorySupport.expectNoProblems(play);
                })
                // The Annotation tab (the default) as an editable partner for AnnotationReadOnly
                .story("AnnotationEditable", context -> render(context, fixtures(ENTRIES).build(), false))
                .withPlay(play -> {
                    waitForAnnotation(play);
                    ContentStorySupport.expectNoProblems(play);
                })
                // The Annotation tab (the default) when the user may only view the annotation
                .story("AnnotationReadOnly", context -> render(context, fixtures(ENTRIES).build(), true))
                .withPlay(play -> {
                    waitForAnnotation(play);
                    play.expect(play.getByText("Read only", ".docTab-readOnlyNote")).toBeVisible();
                    ContentStorySupport.expectNoProblems(play);
                })
                // The Events tab as an editable partner for EventsReadOnly
                .story("EventsEditable", context -> render(context, fixtures(ENTRIES).build(), false))
                .withPlay(play -> {
                    openTab(play, "Events", "Add Event Link");
                    ContentStorySupport.expectNoProblems(play);
                })
                // The Events tab when the user may only view the annotation
                .story("EventsReadOnly", context -> render(context, fixtures(ENTRIES).build(), true))
                .withPlay(play -> {
                    openTab(play, "Events", "Add Event Link");
                    play.expect(play.getByText("Read only", ".docTab-readOnlyNote")).toBeVisible();
                    ContentStorySupport.expectNoProblems(play);
                })
                // The Link To tab as an editable partner for LinkToReadOnly
                .story("LinkToEditable", context -> render(context, fixtures(ENTRIES).build(), false))
                .withPlay(play -> {
                    openTab(play, "Link To", "Add Annotation Link");
                    ContentStorySupport.expectNoProblems(play);
                })
                // The Link To tab when the user may only view the annotation
                .story("LinkToReadOnly", context -> render(context, fixtures(ENTRIES).build(), true))
                .withPlay(play -> {
                    openTab(play, "Link To", "Add Annotation Link");
                    play.expect(play.getByText("Read only", ".docTab-readOnlyNote")).toBeVisible();
                    ContentStorySupport.expectNoProblems(play);
                })
                // The Status block shows the current value; clicking it opens a chooser; picking a
                // status sends a setTag change
                .story("StatusSettingBlock", context -> render(context, fixtures(ENTRIES).build()))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.findByDisplayValue("Investigate alert");
                    play.click(play.getByText("Status"));
                    // Differs from React: GWT's status chooser is a list, with no selection box
                    play.click(screen.findByText("Closed"));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post(CHANGE_PATH)
                                    .withJsonBodyContaining("{\"change\": {\"type\": \"setTag\", "
                                            + "\"tag\": {\"name\": \"Closed\"}}}")
                                    .toSpyMatcher()));
                    ContentStorySupport.expectNoProblems(play);
                });
    }

    // Waits for the Annotation tab's title and history
    private static void waitForAnnotation(final Play play) {
        play.findByDisplayValue("Investigate alert");
        play.findByText("Looking into it.");
    }

    // Opens a sub-tab of the annotation and waits for its button with this title
    private static void openTab(final Play play, final String name, final String buttonTitle) {
        play.findByDisplayValue("Investigate alert");
        play.click(tab(play, name));
        play.findByTitle(buttonTitle);
    }

    // A sub-tab of the annotation (Stroom's LinkTabPanel has no tab role)
    private static Query tab(final Play play, final String name) {
        return play.getByText(name, StroomDom.LINK_TAB_LABEL);
    }

    private static RestFixtures.Builder fixtures(final String entries) {
        return AnnotationFixtures.editorRoutes(RestFixtures.builder(), entries);
    }

    private static RestReply meta(final RecordedRequest request) {
        final String id = request.getPath().substring(request.getPath().lastIndexOf('/') + 1);
        return RestReply.json("{\"id\": " + id + ", \"feedName\": \"TEST_FEED\", \"typeName\": \"Events\", "
                + "\"status\": \"UNLOCKED\", \"createMs\": 1700000000000, \"effectiveMs\": 1700000000000}");
    }

    // As the React story's loadSource does: the data names the stream and record asked for
    private static RestReply fetchData(final RecordedRequest request) {
        final Map<?, ?> body = (Map<?, ?>) JsonValues.parse(request.getBody());
        final Map<?, ?> location = (Map<?, ?>) body.get("sourceLocation");
        final Object metaId = location.get("metaId");
        final Object recordIndex = location.get("recordIndex");
        final String data = "source for stream " + metaId + " record " + recordIndex;
        return RestReply.json("{\"type\": \"data\", \"feedName\": \"TEST_FEED\", \"streamTypeName\": \"Events\", "
                + "\"sourceLocation\": {\"metaId\": " + metaId + ", \"partIndex\": 0, \"recordIndex\": "
                + recordIndex + "}, \"itemRange\": {\"offset\": 0, \"length\": 1}, "
                + "\"totalItemCount\": {\"count\": 1, \"exact\": true}, "
                + "\"totalCharacterCount\": {\"count\": 40, \"exact\": true}, \"data\": \"" + data + "\", "
                + "\"html\": false, \"dataType\": \"NON_SEGMENTED\", \"displayMode\": \"TEXT\"}");
    }

    private static Widget render(final StoryContext context, final RestFixtures fixtures) {
        return render(context, fixtures, false);
    }

    private static Widget render(final StoryContext context, final RestFixtures fixtures, final boolean readOnly) {
        final ContentScreenGinjector injector = GWT.create(ContentScreenGinjector.class);
        final ScreenHarness harness = ScreenHarness.builder(context, fixtures)
                .injector(injector)
                // The confirmations are answered in Stroom's real dialog
                .realAlerts()
                .build();
        // The annotation chooser, as its GWTP proxy would show it
        final FindAnnotationPresenter findAnnotationPresenter = injector.getFindAnnotationPresenter();
        harness.addRegistration(harness.getEventBus().addHandler(ShowFindAnnotationEvent.getType(),
                findAnnotationPresenter));
        harness.afterStartUp(() -> AnnotationFixtures.open(injector, harness, 42L, readOnly));
        return harness.asWidget();
    }
}
