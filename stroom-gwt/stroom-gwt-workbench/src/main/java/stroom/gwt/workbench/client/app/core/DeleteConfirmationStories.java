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

package stroom.gwt.workbench.client.app.core;

import stroom.core.client.HasSaveRegistry;
import stroom.docref.DocRef;
import stroom.document.client.DocumentPluginEventManager;
import stroom.document.client.DocumentPluginRegistry;
import stroom.document.client.event.DeleteDocumentEvent;
import stroom.explorer.client.presenter.DocumentTypeCache;
import stroom.explorer.shared.DeleteConfirmation;
import stroom.gwt.workbench.client.StoryPanels;
import stroom.gwt.workbench.client.app.rest.RequestMatcher;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.client.app.screen.StroomDom;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.TextMatch;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;

import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/// Stories matching `App/Core/deleteConfirmation` in the React Storybook, which tests the React
/// port's delete confirmation as pure functions and their orchestration. Here they are the rules as
/// Stroom applies them: Stroom's real `DocumentPluginEventManager` handles a `DeleteDocumentEvent`
/// (as the explorer's Delete does), fetches the `DeleteConfirmation` (`POST
/// /explorer/v2/fetchDeleteConfirmation`) and asks with Stroom's real confirmation dialog, whose
/// message and detail (`buildDeleteWarningMessage`, `buildDeleteConfirmationDetail`) the plays check.
///
/// The story shows a button per case (the documents to delete and the confirmation the server
/// returns), which fires the event; React's `onResult` is a spy on the event's `ResultCallback`, and
/// its `confirm`/`confirmWarnHtml` seams are the confirmation spy (`QUESTION` for the plain question,
/// `WARN` for the warning). `isDeleteConfirmationEmpty` is Stroom's `DeleteConfirmation.isEmpty`.
public final class DeleteConfirmationStories {

    /// The name of the spy recording the delete's result.
    static final String ON_RESULT = "onResult";

    private static final String FETCH_PATH = "/explorer/v2/fetchDeleteConfirmation";
    private static final String DELETE_PATH = "/explorer/v2/delete";

    // React's CHILD_ONLY and DEPENDANTS_ONLY
    private static final String CHILD_ONLY = """
            "totalChildCount": 2, "childTypeCounts": {"Dictionary": 2},
            "childItems": [{"type": "Dictionary", "uuid": "d1", "name": "Countries"},
                           {"type": "Dictionary", "uuid": "d2", "name": "Colours"}]""";
    private static final String DEPENDANTS_ONLY = """
            "visibleDependants": [{"type": "Pipeline", "uuid": "p1", "name": "Events Pipeline"}]""";

    private DeleteConfirmationStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Core/deleteConfirmation", DeleteConfirmationStories.class)
                // Nothing contained and nothing depending on it: the plain question
                .story("EmptyConfirmationAsksThePlainQuestion",
                        context -> new Label("See the play function."))
                .withPlay(play -> {
                    // Differs from React: Stroom's DeleteConfirmation.isEmpty, on confirmations as
                    // the server sends them
                    play.expect("{} is empty", () -> confirmation(null, null, null).isEmpty()).toBe(true);
                    play.expect("all items hidden isn't empty",
                            () -> confirmation(null, true, null).isEmpty()).toBe(false);
                    play.expect("hidden dependants isn't empty",
                            () -> confirmation(null, null, true).isEmpty()).toBe(false);
                    play.expect("no children is empty", () -> confirmation(0, null, null).isEmpty()).toBe(true);
                })
                // buildDeleteWarningMessage: three bodies, singular and plural
                .story("WarningMessageWording", context -> render(context,
                        new Case("one-children", 1, "{" + CHILD_ONLY + "}"),
                        new Case("three-children", 3, "{" + CHILD_ONLY + "}"),
                        new Case("one-dependants", 1, "{" + DEPENDANTS_ONLY + "}"),
                        new Case("two-both", 2, "{" + CHILD_ONLY + ", " + DEPENDANTS_ONLY + "}")))
                .withPlay(play -> {
                    expectMessage(play, "one-children",
                            "This item contains other items. Are you sure you want to delete it?");
                    expectMessage(play, "three-children",
                            "These 3 items contain other items. Are you sure you want to delete them?");
                    expectMessage(play, "one-dependants",
                            "This item is used by other items. Deleting it may break those items. "
                            + "Are you sure you want to delete it?");
                    expectMessage(play, "two-both",
                            "These 2 items contain other items and are used by items elsewhere. "
                            + "Are you sure you want to delete them?");
                    expectNoProblems(play);
                })
                // Type counts use the display names, by descending count then type
                .story("TypeCountLinesOrdering", context -> render(context,
                        new Case("counts", 1, """
                                {"totalChildCount": 9, "childTypeCounts": {"Dictionary": 2, "Feed": 5, "XSLT": 2},
                                 "childItems": []}"""),
                        new Case("unknown", 1, """
                                {"totalChildCount": 1, "childTypeCounts": {"NotAType": 1}, "childItems": []}""")))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    open(play, "counts");
                    // Feed first (highest count); the two 2s by type, not display name
                    play.expect(screen.getAllByText(TextMatch.regex("\\(\\d+\\)$")).textContents())
                            .toEqual(List.of("Feed (5)", "Dictionary (2)", "XSL Translation (2)"));
                    cancel(play);
                    // An unknown type falls back to the type
                    open(play, "unknown");
                    play.expect(screen.getByText("NotAType (1)")).toBeInTheDocument();
                    cancel(play);
                    expectNoProblems(play);
                })
                // Contained items are summarised, listed, and truncation flagged
                .story("DetailListsContainedItems", context -> render(context,
                        new Case("truncated", 1, "{" + CHILD_ONLY + ", \"childItemsTruncated\": true}"),
                        new Case("single", 1, """
                                {"totalChildCount": 1, "childTypeCounts": {"Dictionary": 1},
                                 "childItems": [{"type": "Dictionary", "uuid": "d1", "name": "Countries"}]}""")))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    open(play, "truncated");
                    play.expect(screen.getByText("The following 2 contained items will also be deleted:"))
                            .toBeInTheDocument();
                    play.expect(screen.getByText("Dictionary (2)")).toBeInTheDocument();
                    play.expect(screen.getByText("Countries")).toBeInTheDocument();
                    play.expect(screen.getByText("…and more")).toBeInTheDocument();
                    cancel(play);
                    // One item takes the singular header
                    open(play, "single");
                    play.expect(screen.getByText("The following contained item will also be deleted:"))
                            .toBeInTheDocument();
                    cancel(play);
                    expectNoProblems(play);
                })
                // Hidden dependants are disclosed, not named
                .story("HiddenDependantsAreDisclosedButNotNamed", context -> render(context,
                        new Case("visible-and-hidden", 1, """
                                {"visibleDependants": [{"type": "Pipeline", "uuid": "p1", "name": "Visible Pipeline"}],
                                 "hasHiddenDependants": true}"""),
                        new Case("hidden-only", 1, "{\"hasHiddenDependants\": true}")))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    open(play, "visible-and-hidden");
                    play.expect(screen.getByText("The following items depend on this:")).toBeInTheDocument();
                    play.expect(screen.getByText("Visible Pipeline")).toBeInTheDocument();
                    play.expect(screen.getByText(TextMatch.containing(
                            "There are also dependants that you do not have permission to view."))).toBeInTheDocument();
                    cancel(play);
                    // With only hidden dependants there is no list, just the disclosure
                    open(play, "hidden-only");
                    play.expect(screen.queryByText("The following items depend on this:")).toBeNull();
                    play.expect(screen.getByText(TextMatch.containing(
                            "There are also dependants that you do not have permission to view."))).toBeInTheDocument();
                    cancel(play);
                    expectNoProblems(play);
                })
                // Hidden child items mean the folder survives
                .story("HiddenChildItemsExplainTheFolderWillSurvive", context -> render(context,
                        new Case("with-visible", 1, "{" + CHILD_ONLY + ", \"hasHiddenChildItems\": true}"),
                        new Case("hidden-only", 1, "{\"totalChildCount\": 0, \"hasHiddenChildItems\": true}")))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    open(play, "with-visible");
                    play.expect(screen.getByText(TextMatch.containing("The folder also contains items you do not "
                            + "have permission to delete, so the folder itself will not be removed.")))
                            .toBeInTheDocument();
                    cancel(play);
                    open(play, "hidden-only");
                    play.expect(screen.getByText(TextMatch.containing("This folder contains items you do not have "
                            + "permission to delete, so it cannot be removed."))).toBeInTheDocument();
                    cancel(play);
                    expectNoProblems(play);
                })
                // Names are escaped
                .story("DocumentNamesAreEscaped", context -> render(context,
                        new Case("script-name", 1, """
                                {"visibleDependants": [{"type": "Pipeline", "uuid": "p1",
                                                        "name": "<img src=x onerror=alert(1)>"}]}""")))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    open(play, "script-name");
                    // Differs from React: the dialog shows the detail, so the name's text is checked,
                    // and that no image was made of it
                    play.expect(screen.getByText("<img src=x onerror=alert(1)>")).toBeInTheDocument();
                    play.expect(screen.querySelectorAll(StroomDom.DIALOG + " img")).toHaveLength(0);
                    cancel(play);
                    expectNoProblems(play);
                })
                // Nothing of note: the plain question (ConfirmEvent.fire, not fireWarn)
                .story("OrchestrationUsesPlainConfirmWhenEmpty", context -> render(context,
                        new Case("empty", 1, "{}")))
                .withPlay(play -> {
                    open(play, "empty");
                    play.expect(play.spy(ScreenHarness.CONFIRM_SPY))
                            .toHaveBeenCalledWith("QUESTION: Are you sure you want to delete this item?");
                    play.expect(play.spy(ScreenHarness.CONFIRM_SPY)).toHaveBeenCalledTimes(1);
                    cancel(play);
                    expectNoProblems(play);
                })
                // A failed lookup still warns, and still allows the delete
                .story("FailedLookupStillAllowsTheDelete", context -> render(context,
                        new Case("lookup-fails", 1, null)))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    open(play, "lookup-fails");
                    play.expect(play.spy(ScreenHarness.CONFIRM_SPY)).toHaveBeenCalledWith(
                            "WARN: Unable to check what would be affected by deleting this item. "
                            + "Are you sure you want to delete it anyway?");
                    play.expect(screen.getByText(TextMatch.containing("The check failed:"))).toBeInTheDocument();
                    play.expect(screen.getByText(TextMatch.containing("server exploded"))).toBeInTheDocument();
                    // Agreeing proceeds
                    play.click(screen.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.delete(DELETE_PATH).toSpyMatcher()));
                    play.waitFor(() -> play.expect(play.spy(ON_RESULT)).toHaveBeenCalledWith("true"));
                    expectNoProblems(play);
                })
                // React: onResult reports a cancel too
                .story("CancelIsReported", context -> render(context, new Case("cancel", 1, "{}")))
                .withPlay(play -> {
                    open(play, "cancel");
                    cancel(play);
                    // The event's callback is told about the cancel (it once only heard about an OK,
                    // leaving a caller waiting on the result). Nothing is deleted
                    play.waitFor(() -> play.expect(play.spy(ON_RESULT)).toHaveBeenCalledWith("false"));
                    play.expect(play.spy(ScreenHarness.REQUEST_SPY)).not().toHaveBeenCalledWith(
                            RequestMatcher.delete(DELETE_PATH).toSpyMatcher());
                    expectNoProblems(play);
                });
    }

    /// @return Stroom's delete confirmation, as the server sends it.
    private static DeleteConfirmation confirmation(final Integer totalChildCount,
                                                   final Boolean hasHiddenChildItems,
                                                   final Boolean hasHiddenDependants) {
        return new DeleteConfirmation(null, totalChildCount, null, hasHiddenChildItems, null, null,
                hasHiddenDependants);
    }

    /// Deletes a case's documents, and waits for the confirmation.
    private static void open(final Play play, final String caseId) {
        play.click(play.getByTestId("delete-" + caseId));
        final Play screen = play.screen();
        play.waitFor(() -> play.expect(screen.querySelector(StroomDom.DIALOG)).toBeInTheDocument());
    }

    /// Checks the confirmation's message, then cancels it.
    private static void expectMessage(final Play play, final String caseId, final String message) {
        open(play, caseId);
        play.expect(play.spy(ScreenHarness.CONFIRM_SPY)).toHaveBeenCalledWith("WARN: " + message);
        play.expect(play.screen().getByText(message)).toBeInTheDocument();
        cancel(play);
    }

    /// Cancels the confirmation, and waits for it to close.
    private static void cancel(final Play play) {
        final Play screen = play.screen();
        play.click(screen.getByRole("button", StroomDom.button("Cancel")));
        play.waitFor(() -> play.expect(screen.querySelectorAll(StroomDom.DIALOG)).toHaveLength(0));
    }

    private static void expectNoProblems(final Play play) {
        play.expect(play.spy(ScreenHarness.ALERT_SPY)).not().toHaveBeenCalled();
        play.expect(play.spy(ScreenHarness.UNHANDLED_REQUEST_SPY)).not().toHaveBeenCalled();
    }

    private static Widget render(final StoryContext context, final Case... cases) {
        final RestFixtures.Builder fixtures = RestFixtures.builder();
        for (final Case deleteCase : cases) {
            final RequestMatcher matcher = RequestMatcher.post(FETCH_PATH)
                    .withBody("the documents of case " + deleteCase.id,
                            body -> body != null && body.contains("\"" + deleteCase.id + "-0\""));
            fixtures.route(matcher, deleteCase.confirmation == null
                    ? RestReply.error(500, "server exploded")
                    : RestReply.json(deleteCase.confirmation));
        }
        fixtures.route(RequestMatcher.delete(DELETE_PATH), RestReply.json("{\"explorerNodes\": []}"));
        final ScreenHarness harness = ScreenHarness.builder(context, fixtures.build())
                .realAlerts()
                .build();
        harness.fn(ON_RESULT);

        // Stroom's handler of DeleteDocumentEvent, as the app creates (and binds) it
        final DocumentPluginEventManager manager = new DocumentPluginEventManager(harness.getEventBus(),
                new HasSaveRegistry(),
                harness.getRestFactory(),
                new DocumentTypeCache(harness.getRestFactory()),
                new DocumentPluginRegistry(),
                harness.getSecurityContext());
        manager.bind();
        harness.addCleanUp(manager::unbind);

        final FlowPanel panel = new FlowPanel();
        for (final Case deleteCase : cases) {
            final Button button = new Button("Delete: " + deleteCase.id, (ClickHandler)
                    event -> DeleteDocumentEvent.fire(harness.getHasHandlers(), deleteCase.docRefs(), true,
                            ok -> harness.spy(ON_RESULT, String.valueOf(ok)), null));
            button.getElement().setAttribute("data-testid", "delete-" + deleteCase.id);
            panel.add(StoryPanels.row(8, button));
        }
        harness.add(panel);
        return harness.asWidget();
    }


    // --------------------------------------------------------------------------------


    /// A delete: the documents (Dictionaries `<id>-0`, `<id>-1`...) and the confirmation the
    /// server returns for them (null for a failed lookup).
    private static final class Case {

        private final String id;
        private final int count;
        private final String confirmation;

        Case(final String id, final int count, final String confirmation) {
            this.id = id;
            this.count = count;
            this.confirmation = confirmation;
        }

        List<DocRef> docRefs() {
            final List<DocRef> docRefs = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                docRefs.add(new DocRef("Dictionary", id + "-" + i, id + " " + i));
            }
            return Collections.unmodifiableList(docRefs);
        }
    }
}
