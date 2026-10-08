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

package stroom.gwt.workbench.client.app.editors;

import stroom.docref.DocRef;
import stroom.gwt.workbench.client.app.editors.DocEditors.DocResource;
import stroom.gwt.workbench.client.app.rest.RequestMatcher;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.client.app.screen.StroomDom;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.pathways.client.presenter.PathwaysPresenter;
import stroom.pathways.client.presenter.ShowTracesEvent;
import stroom.pathways.shared.PathwaysDoc;
import stroom.pathways.shared.PathwaysResource;
import stroom.pathways.shared.pathway.PathNode;
import stroom.pathways.shared.pathway.PathNodeSequence;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;

import java.util.stream.Collectors;

/// Stories matching `App/Editors/PathwaysEditor` in the React Storybook, showing Stroom's real
/// [PathwaysPresenter] (a Pathways document's tab: Pathways, Settings, Documentation and
/// Permissions) with fake REST replies.
///
/// As `PathwaysPlugin` does, the story fetches the document (`GET /pathways/v2/{uuid}`) and reads
/// it into the editor ([DocEditors#open]). React's `PathwaysApi` seam is Stroom's
/// `PathwaysResource`: `findPathways` → `POST /pathways/v2/findPathways`, `addPathway` →
/// `POST /pathways/v2/addPathway`, `updatePathway` and `deletePathway` likewise; `loadNodes` → the
/// trace store and feed selection boxes' explorer routes; `onViewTraces` → a spy on
/// `ShowTracesEvent` (the trace store's UUID and the pruned pathway's root and target UUIDs).
public final class PathwaysEditorStories {

    /// The name of the spy recording the traces asked for (React's `onViewTraces`).
    static final String ON_VIEW_TRACES = "onViewTraces";

    // PathwaysResource.fetch(): React's PATHWAYS_DOC
    private static final String DOC = """
            {"type": "Pathways", "uuid": "UUID", "name": "My Pathways",
              "tracesDocRef": {"type": "PlanB", "uuid": "planb-1", "name": "My Traces"},
              "infoFeed": {"type": "Feed", "uuid": "feed-1", "name": "My Feed"},
              "temporalOrderingTolerance": {"time": 5, "timeUnit": "SECONDS"},
              "allowPathwayCreation": true, "allowPathwayMutation": true, "allowConstraintCreation": true,
              "allowConstraintMutation": true, "processingNode": "node1", "description": "# Pathways docs"}""";

    // PathwaysResource.findPathways(): React's two pathways ('login-flow' with a constraint on Auth)
    private static final String PATHWAYS = """
            {"values": [
                {"name": "login-flow", "createTime": {"seconds": 1700000000, "nanos": 0},
                  "updateTime": {"seconds": 1700001000, "nanos": 0},
                  "root": {"uuid": "n1", "name": "Start", "targets": [
                    {"uuid": "s1", "nodes": [
                      {"uuid": "n2", "name": "Auth", "targets": [], "constraints": {
                        "host": {"name": "host", "optional": false, "value": {"type": "anyValue"}}}}]}]}},
                {"name": "checkout", "createTime": {"seconds": 1700002000, "nanos": 0},
                  "root": {"uuid": "c1", "name": "Cart", "targets": []}}],
              "pageResponse": {"offset": 0, "length": 2, "total": 2, "exact": true}}""";

    // React's branchingApi: Start → [A, B]
    private static final String BRANCHING = """
            {"values": [
                {"name": "branchy", "createTime": {"seconds": 1700000000, "nanos": 0},
                  "root": {"uuid": "r", "name": "Start", "targets": [
                    {"uuid": "sa", "nodes": [{"uuid": "a", "name": "A", "targets": []}]},
                    {"uuid": "sb", "nodes": [{"uuid": "b", "name": "B", "targets": []}]}]}}],
              "pageResponse": {"offset": 0, "length": 1, "total": 1, "exact": true}}""";

    // Differs from React: a FormGroup gives its control the group's identity as its id
    // (PathwaysSettingsViewImpl.ui.xml and its dialogs), not React's '<name>-input'
    private static final String PROCESSING_NODE = "#pathwaysProcessingNode";

    private PathwaysEditorStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Editors/PathwaysEditor", PathwaysEditorStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // Pathways is the default tab: the list of pathways and the graph of the selected one
                .story("Default", context -> render(context, "pathways-1", PATHWAYS, false))
                .withPlay(play -> {
                    for (final String label : new String[]{"Pathways", "Settings", "Documentation", "Permissions"}) {
                        play.waitFor(() -> play.expect(DocEditors.tab(play, label)).toBeInTheDocument());
                    }
                    play.waitFor(() -> play.expect(play.getByText("login-flow")).toBeInTheDocument());
                    play.expect(play.getByText("checkout")).toBeInTheDocument();
                    // Differs from React: the graph pane has no 'select a pathway' prompt; it shows
                    // no nodes until a pathway is selected
                    play.expect(play.querySelector(".pathway-nodeName")).toBeNull();
                    play.expect(play.getByRole("button", "Save")).toHaveClass("disabled");
                    DocEditors.expectNoProblems(play);
                })
                // Selecting a pathway shows its graph; a node can be selected
                .story("Graph", context -> render(context, "pathways-1", PATHWAYS, false))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.click(play.findByText("login-flow"));
                    play.waitFor(() -> play.expect(play.getByText("Start")).toBeInTheDocument());
                    play.expect(play.getByText("Auth")).toBeInTheDocument();
                    play.click(play.getByText("Auth"));
                    play.waitFor(() -> play.expect(play.getByText("Auth")).toHaveClass("pathway-nodeName--selected"));
                    // Differs from React: the main graph has no node detail panel; a node's
                    // constraints are shown, as a grid (ConstraintListPresenter), in the Edit
                    // Pathway dialog, so the play opens it and selects the node there
                    play.click(play.getByRole("button", "Edit Pathway"));
                    final Play dialog = play.within(screen.findByText("Edit Pathway", StroomDom.DIALOG_TITLE)
                            .closest(StroomDom.DIALOG));
                    play.click(dialog.findByText("Auth"));
                    play.waitFor(() -> play.expect(dialog.getByText("host")).toBeInTheDocument());
                    final Play constraints = play.within(dialog.getByText("host").closest(".dataGridWidget"));
                    play.expect(constraints.getByText("Name")).toBeInTheDocument();
                    play.expect(constraints.getByText("Optional")).toBeInTheDocument();
                    // Differs from React: the value's type is shown as Stroom's ConstraintValueType
                    play.expect(constraints.getByText("Any")).toBeInTheDocument();
                    DocEditors.expectNoProblems(play);
                })
                // View Matching Traces: the trace store and the pathway pruned to the selected node's
                // ancestors (selecting B drops the sibling A branch)
                .story("ViewTraces", context -> render(context, "pathways-2", BRANCHING, false))
                .withPlay(play -> {
                    play.click(play.findByText("branchy"));
                    play.waitFor(() -> play.expect(play.getByText("Start")).toBeInTheDocument());
                    play.click(play.getByText("B"));
                    play.click(play.findByRole("button", "View Matching Traces"));
                    play.waitFor(() -> play.expect(play.spy(ON_VIEW_TRACES)).toHaveBeenCalledTimes(1));
                    play.expect(play.spy(ON_VIEW_TRACES)).toHaveBeenCalledWith("planb-1", "r", "sb");
                    DocEditors.expectNoProblems(play);
                })
                // NewPathway is blocked by a GWT bug (see react-story-status.json): New Pathway
                // reads a pathway with no root into the dialog's graph, and
                // PathwayTreePresenter.read calls addNode(null), which throws
                // Edit Pathway: the dialog's graph and a node's constraints (React goes on to add a
                // constraint, which GWT doesn't allow, see below)
                .story("EditConstraints", context -> render(context, "pathways-1", PATHWAYS, false))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.click(play.findByText("login-flow"));
                    play.click(play.getByRole("button", "Edit Pathway"));
                    final Play dialog = play.within(screen.findByText("Edit Pathway", StroomDom.DIALOG_TITLE)
                            .closest(StroomDom.DIALOG));
                    play.click(dialog.findByText("Auth"));
                    play.waitFor(() -> play.expect(dialog.getByText("host")).toBeInTheDocument());
                    // Differs from React: the constraint list's buttons stay disabled, titled
                    // 'New constraint disabled as read only' (a GWT bug: ConstraintListPresenter.setData
                    // sets readOnly but never calls enableButtons(), so the buttons keep their
                    // initial read only state), so the New Constraint dialog and its validation
                    // can't be reached
                    play.expect(dialog.getByRole("button", "New constraint disabled as read only"))
                            .toHaveAttribute("aria-disabled", "true");
                    play.expect(dialog.queryByRole("button", "New Constraint")).toBeNull();
                    // Cancel the pathway's dialog
                    play.click(dialog.getByRole("button", StroomDom.button("Cancel")));
                    play.waitFor(() -> play.expect(screen.queryByText("Edit Pathway", StroomDom.DIALOG_TITLE))
                            .toBeNull());
                    DocEditors.expectNoProblems(play);
                })
                // Settings: the document's configuration; an edit enables Save
                .story("Settings", context -> render(context, "pathways-1", PATHWAYS, false))
                .withPlay(play -> {
                    play.waitFor(() -> play.expect(DocEditors.tab(play, "Settings")).toBeInTheDocument());
                    play.click(DocEditors.tab(play, "Settings"));
                    for (final String label : new String[]{"Trace Store", "Info Feed", "Temporal Ordering Tolerance",
                            "Allow Pathway Creation", "Processing Node"}) {
                        play.waitFor(() -> play.expect(play.getByText(label, "label")).toBeInTheDocument());
                    }
                    play.waitFor(() -> play.expect(play.getByText("My Traces")).toBeInTheDocument());
                    final Query save = play.getByRole("button", "Save");
                    play.expect(save).toHaveClass("disabled");
                    play.type(play.querySelector(PROCESSING_NODE), "-x");
                    // Differs from React: the text box reports its change when it loses the focus
                    play.tab();
                    play.waitFor(() -> play.expect(save).not().toHaveClass("disabled"));
                    DocEditors.expectNoProblems(play);
                })
                .story("ReadOnly", context -> render(context, "pathways-1", PATHWAYS, true))
                .withPlay(play -> {
                    final Query save = play.findByRole("button",
                            "Save is not available as this document is read only");
                    play.click(DocEditors.tab(play, "Settings"));
                    final Query processingNode = play.findByDisplayValue("node1");
                    // Differs from React: the Processing Node text box stays enabled (a GWT bug:
                    // PathwaysSettingsViewImpl.onReadOnly only disables the ordering tolerance), but
                    // an edit can't make the read only document dirty, so Save stays disabled
                    play.expect(processingNode).not().toBeDisabled();
                    play.type(processingNode, "-x");
                    play.tab();
                    play.sleep(300);
                    play.expect(save).toHaveClass("disabled");
                    DocEditors.expectNoProblems(play);
                });
    }

    private static Widget render(final StoryContext context,
                                 final String uuid,
                                 final String pathways,
                                 final boolean readOnly) {
        final DocRef docRef = new DocRef(PathwaysDoc.TYPE, uuid, "My Pathways");
        final RestFixtures fixtures = DocEditors.docSelectionRoutes(
                        DocEditors.permissionRoutes(RestFixtures.builder()))
                .get("/pathways/v2/" + uuid, RestReply.json(DOC.replace("UUID", uuid)))
                .put("/pathways/v2/" + uuid, request -> RestReply.json(request.getBody()))
                .post("/pathways/v2/findPathways", RestReply.json(pathways))
                .post("/pathways/v2/addPathway", RestReply.json("true"))
                .post("/pathways/v2/updatePathway", RestReply.json("true"))
                .post("/pathways/v2/deletePathway", RestReply.json("true"))
                .build();
        final PathwaysResource resource = GWT.create(PathwaysResource.class);
        return DocEditors.render(context, fixtures, readOnly, (harness, injector) -> {
            // As TracesPlugin handles the event: the spy records the trace store and the pruned
            // pathway's root and its targets
            final Spy onViewTraces = harness.fn(ON_VIEW_TRACES);
            harness.addRegistration(harness.getEventBus().addHandler(ShowTracesEvent.getType(), event -> {
                final PathNode root = event.getPathway().getRoot();
                onViewTraces.call(event.getDataSourceRef().getUuid(),
                        root.getUuid(),
                        root.getTargets().stream().map(PathNodeSequence::getUuid).collect(Collectors.joining(",")));
            }));
            DocEditors.open(harness,
                    docRef,
                    injector.getPathwaysPresenter(),
                    DocResource.of(
                            restFactory -> restFactory.create(resource).method(res -> res.fetch(docRef.getUuid())),
                            (restFactory, doc) -> restFactory.create(resource)
                                    .method(res -> res.update(doc.getUuid(), doc))));
        });
    }
}
