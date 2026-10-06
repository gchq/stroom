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

import stroom.docref.DocRef;
import stroom.gwt.workbench.client.app.gin.processing.ProcessingScreenGinjector;
import stroom.gwt.workbench.client.app.rest.JsonValues;
import stroom.gwt.workbench.client.app.rest.RequestMatcher;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.client.app.screen.StroomDom;
import stroom.gwt.workbench.client.widgets.tree.ExplorerFixture;
import stroom.gwt.workbench.client.widgets.tree.TreeFixtures;
import stroom.gwt.workbench.framework.client.play.EventInit;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.play.TextMatch;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.pipeline.client.presenter.PipelinePresenter;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.PipelineResource;
import stroom.security.shared.DocumentPermission;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;

import java.util.Map;

/// Stories matching `App/Main/PipelineEditor` in the React Storybook, showing Stroom's real
/// [PipelinePresenter] (a Pipeline's editor tab: Data, Structure, Processors, Active Tasks,
/// Documentation and Permissions), opened as `PipelinePlugin` opens a document, with fake REST
/// replies.
///
/// | React | Stroom |
/// |---|---|
/// | `api.fetch` | `GET /pipeline/v1/{uuid}` |
/// | `api.save` | `PUT /pipeline/v1/{uuid}` |
/// | `api.fetchLayers` | `POST /pipeline/v1/fetchPipelineLayers` (by the pipeline asked for) |
/// | `api.fetchPropertyTypes` | `GET /pipeline/v1/propertyTypes` |
/// | `api.fetchJson` | `POST /pipeline/v1/fetchPipelineJson` |
/// | `meta`, `data` | `POST /meta/v1/find` (no streams) |
/// | `processorFilter.find` | `POST /processorFilter/v1/find` |
/// | `processorTask` | `POST /processorTask/v1/find`, `POST /processorTask/v1/summary` |
/// | `loadNodes` | the explorer tree's `POST /explorer/v2/fetchExplorerNodes` |
/// | `steppingApi` | `SteppingResource` (`PipelineFixtures.stepping`) |
///
/// `MultiDocumentSave` is blocked (see `react-story-status.json`).
public final class PipelineEditorStories {

    /// The name of the spy recording the editor's dirty state (its `DirtyEvent`s).
    static final String ON_DIRTY = "onDirty";
    /// The name of the spy recording embedded documents created.
    static final String ON_EMBED = "onEmbed";

    private static final String UUID = "child";
    private static final DocRef DOC_REF = DocRef.builder()
            .type(PipelineDoc.TYPE)
            .uuid(UUID)
            .name("My Pipeline")
            .build();
    private static final String LAYERS_PATH = "/pipeline/v1/fetchPipelineLayers";

    private static final String CHILD_DATA = """
            {
              "elements": {"add": [{"id": "xslt", "type": "XSLTFilter"}]},
              "links": {"add": [{"from": "parser", "to": "xslt"}]},
              "properties": {"add": [{"element": "xslt", "name": "xslt",
                "value": {"entity": {"type": "XSLT", "uuid": "x1", "name": "My XSLT"}}}]},
              "pipelineReferences": {"add": [{"element": "xslt", "name": "reference",
                "pipeline": {"type": "Pipeline", "uuid": "rp", "name": "Ref Pipeline"},
                "feed": {"type": "Feed", "uuid": "f1", "name": "MY_FEED"}, "streamType": "Reference"}]}
            }""";

    private static final String DOC = """
            {"type": "Pipeline", "uuid": "child", "name": "My Pipeline", "version": "v1",
              "parentPipeline": {"type": "Pipeline", "uuid": "parent", "name": "Parent"},
              "pipelineData": CHILD_DATA}""".replace("CHILD_DATA", CHILD_DATA);

    private static final String LAYERS = """
            [
              {"sourcePipeline": {"type": "Pipeline", "uuid": "parent", "name": "Parent"},
                "pipelineData": {"elements": {"add": [{"id": "parser", "type": "CombinedParser"}]},
                  "links": {"add": [{"from": "Source", "to": "parser"}]}}},
              {"sourcePipeline": {"type": "Pipeline", "uuid": "child", "name": "Child"},
                "pipelineData": CHILD_DATA}
            ]""".replace("CHILD_DATA", CHILD_DATA);

    private static final String NO_ROWS = """
            {"values": [], "pageResponse": {"offset": 0, "length": 0, "total": 0, "exact": true}}""";

    // The processor (expanded) and its filter, for the Processors tab
    private static final String PROCESSOR_ROWS = """
            {
              "values": [
                {"type": "processor", "expander": {"depth": 0, "expanded": true, "leaf": false},
                  "processor": {"id": 9, "enabled": true, "pipelineUuid": "child", "pipelineName": "My Pipeline"}},
                {"type": "processorFilter", "processorFilter": {"id": 1, "enabled": true, "priority": 10,
                  "pipelineUuid": "child", "pipelineName": "My Pipeline", "reprocess": false,
                  "queryData": {"expression": {"type": "operator", "op": "AND", "children": []}},
                  "processorFilterTracker": {"status": "COMPLETE", "metaCount": 5}}}
              ],
              "pageResponse": {"offset": 0, "length": 2, "total": 2, "exact": true}
            }""";

    // Inherit From: two different parent stacks, each with a 'parser' of its own name
    private static final String INHERIT_CHILD = """
            {"elements": {"add": [{"id": "xslt", "type": "XSLTFilter"}]},
              "links": {"add": [{"from": "parser", "to": "xslt"}]}}""";

    private static final String INHERIT_DOC = """
            {"type": "Pipeline", "uuid": "child", "name": "My Pipeline", "version": "v1",
              "parentPipeline": {"type": "Pipeline", "uuid": "parent", "name": "Parent A"},
              "pipelineData": INHERIT_CHILD}""".replace("INHERIT_CHILD", INHERIT_CHILD);

    private static final String BASE_A = """
            {"sourcePipeline": {"type": "Pipeline", "uuid": "parent", "name": "Parent A"},
              "pipelineData": {"elements": {"add": [{"id": "parser", "type": "CombinedParser", "name": "Parser A"}]},
                "links": {"add": [{"from": "Source", "to": "parser"}]}}}""";

    private static final String BASE_B = """
            {"sourcePipeline": {"type": "Pipeline", "uuid": "parent2", "name": "Parent B"},
              "pipelineData": {"elements": {"add": [{"id": "parser", "type": "CombinedParser", "name": "Parser B"}]},
                "links": {"add": [{"from": "Source", "to": "parser"}]}}}""";

    private static final String INHERIT_LAYERS = """
            [BASE_A, {"sourcePipeline": {"type": "Pipeline", "uuid": "child", "name": "My Pipeline"},
              "pipelineData": INHERIT_CHILD}]""".replace("BASE_A", BASE_A).replace("INHERIT_CHILD", INHERIT_CHILD);

    // Reference remove/restore: the base layer has an inherited reference on xslt, the child is empty
    private static final String REF_DOC = """
            {"type": "Pipeline", "uuid": "child", "name": "My Pipeline", "version": "v1",
              "parentPipeline": {"type": "Pipeline", "uuid": "parent", "name": "Parent"},
              "pipelineData": {}}""";

    private static final String REF_LAYERS = """
            [
              {"sourcePipeline": {"type": "Pipeline", "uuid": "parent", "name": "Parent"},
                "pipelineData": {
                  "elements": {"add": [{"id": "parser", "type": "CombinedParser"},
                    {"id": "xslt", "type": "XSLTFilter"}]},
                  "links": {"add": [{"from": "Source", "to": "parser"}, {"from": "parser", "to": "xslt"}]},
                  "pipelineReferences": {"add": [{"element": "xslt", "name": "reference",
                    "pipeline": {"type": "Pipeline", "uuid": "ref1", "name": "Inherited Ref"},
                    "feed": {"type": "Feed", "uuid": "rf1", "name": "REF_FEED"}, "streamType": "Reference"}]}}},
              {"sourcePipeline": {"type": "Pipeline", "uuid": "child", "name": "My Pipeline"}, "pipelineData": {}}
            ]""";

    // The streams of the stepping mode's list
    private static final String STREAMS = """
            {
              "values": [
                {"meta": {"id": 101, "typeName": "Raw Events", "feedName": "MY_FEED", "status": "UNLOCKED",
                  "createMs": 1700000000000}, "attributes": {}},
                {"meta": {"id": 102, "typeName": "Raw Events", "feedName": "MY_FEED", "status": "UNLOCKED",
                  "createMs": 1700000100000}, "attributes": {}}
              ],
              "pageResponse": {"offset": 0, "length": 2, "total": 2, "exact": true}
            }""";

    private static final RestFixtures FIXTURES = fixtures(DOC, NO_ROWS, NO_ROWS,
            RestFixtures.builder().post(LAYERS_PATH, RestReply.json(LAYERS)));
    // The processor collapsed (no expanded rows asked for): its filter isn't sent
    private static final String COLLAPSED_PROCESSOR_ROWS = """
            {
              "values": [
                {"type": "processor", "expander": {"depth": 0, "expanded": false, "leaf": false},
                  "processor": {"id": 9, "enabled": true, "pipelineUuid": "child", "pipelineName": "My Pipeline"}}
              ],
              "pageResponse": {"offset": 0, "length": 1, "total": 1, "exact": true}
            }""";

    private static final RestFixtures PROCESSOR_FIXTURES = fixtures(DOC, NO_ROWS, PROCESSOR_ROWS,
            RestFixtures.builder()
                    .post(LAYERS_PATH, RestReply.json(LAYERS))
                    .route(RequestMatcher.post("/processorFilter/v1/find")
                                    .withJsonBodyContaining("{\"expandedRows\": []}"),
                            RestReply.json(COLLAPSED_PROCESSOR_ROWS)));
    private static final RestFixtures STEPPING_FIXTURES = fixtures(DOC, STREAMS, NO_ROWS,
            RestFixtures.builder().post(LAYERS_PATH, RestReply.json(LAYERS)));
    private static final RestFixtures INHERIT_FIXTURES = fixtures(INHERIT_DOC, NO_ROWS, NO_ROWS,
            RestFixtures.builder()
                    .route(RequestMatcher.post(LAYERS_PATH).withJsonBodyContaining("{\"uuid\": \"parent2\"}"),
                            RestReply.json("[" + BASE_B + "]"))
                    .post(LAYERS_PATH, RestReply.json(INHERIT_LAYERS)));
    private static final RestFixtures REF_FIXTURES = fixtures(REF_DOC, NO_ROWS, NO_ROWS,
            RestFixtures.builder().post(LAYERS_PATH, RestReply.json(REF_LAYERS)));

    private PipelineEditorStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Main/PipelineEditor", PipelineEditorStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // The structure, a property, its reference loaders, the source and the add/remove actions
                .story("Structure", context -> render(context, FIXTURES))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    openStructure(play);
                    // Structure loads: Source -> parser -> xslt
                    play.findByText("Source");
                    play.findByText("parser");
                    play.findByText("xslt");
                    // Selecting the XSLT Filter shows its properties and the document of its xslt
                    play.click(play.getByText("xslt"));
                    play.waitFor(() -> play.expect(play.getByText("The XSLT to use")).toBeInTheDocument());
                    play.expect(play.getByText("My XSLT")).toBeInTheDocument();
                    // Double clicking the property opens the Edit Property dialog for it
                    play.dblClick(play.getByText("My XSLT"));
                    final Play property = dialog(screen, "Edit Property");
                    // Differs from React: GWT shows the element id and name as text, not as fields
                    play.expect(property.getAllByText("xslt").count()).toBeGreaterThan(0);
                    play.click(property.getByRole("button", StroomDom.button("Cancel")));
                    play.waitFor(() -> play.expect(screen.queryByText("Edit Property", StroomDom.DIALOG_TITLE))
                            .toBeNull());
                    // The reference loader grid shows the element's references, with a New button
                    play.expect(play.getByText("Ref Pipeline")).toBeInTheDocument();
                    play.expect(play.getByText("MY_FEED")).toBeInTheDocument();
                    play.expect(play.getByTitle("New Reference")).toBeInTheDocument();
                    // View Source opens the pipeline's JSON, captioned 'Pipeline Source'.
                    // Differs from React: 'View Source' is a GWT Hyperlink (a link, not a button)
                    play.click(play.getByText("View Source"));
                    final Play source = dialog(screen, "Pipeline Source");
                    play.click(source.getByRole("button", StroomDom.button("Cancel")));
                    play.waitFor(() -> play.expect(screen.queryByText("Pipeline Source", StroomDom.DIALOG_TITLE))
                            .toBeNull());
                    // The Add menu for the parser offers the categories valid as its children
                    play.click(play.getByText("parser"));
                    play.click(play.getByTitle("Add New Pipeline Element"));
                    screen.findByText("Filter", StroomDom.MENU_ITEM_TEXT);
                    play.keyboard("{Escape}");
                    // Remove the (local) xslt element, after the confirmation: the tree updates
                    play.click(play.getByText("xslt"));
                    play.click(play.getByTitle("Remove Pipeline Element"));
                    play.click(dialogWith(screen, "Are you sure you want to remove this element?")
                            .getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.queryByText("xslt")).toBeNull());
                    expectNoProblems(play);
                })
                // Setting a document property's Source to Embedded creates an embedded document
                .story("EmbeddedProperty", context -> render(context, FIXTURES))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    openStructure(play);
                    play.click(play.findByText("xslt"));
                    play.dblClick(play.findByText("My XSLT"));
                    final Play property = dialog(screen, "Edit Property");
                    // Change Source (Local) to Embedded with its selection box
                    play.click(property.querySelector(StroomDom.SELECTION_BOX));
                    play.click(screen.findByText("Embedded"));
                    // The value names the embedded document that OK would create
                    play.waitFor(() -> play.expect(property.getByDisplayValue("EMBEDDED XSLT (xslt)"))
                            .toBeInTheDocument());
                    // Differs from React: OK creates the embedded XSLT with the XSLT DocumentPlugin
                    // (PropertyListPresenter.createEmbeddedDocument), which only the app registers in
                    // DocumentPluginRegistry (as its plugins start), so the story stops before OK
                    play.expect(play.spy(ScreenHarness.UNHANDLED_REQUEST_SPY)).not().toHaveBeenCalled();
                })
                // The Structure tab toggles into stepping mode with a stream list
                .story("SteppingMode", context -> render(context, STEPPING_FIXTURES))
                .withPlay(play -> {
                    openStructure(play);
                    play.findByText("parser");
                    play.click(play.findByTitle("Enter Stepping Mode"));
                    // The stream list shows; selecting a stream mounts the stepper.
                    // Differs from React: GWT's stream list is a grid of streams, not a listbox
                    play.click(play.findAllByText("MY_FEED").nth(0));
                    play.findByTitle("Step Forward");
                    expectNoProblems(play);
                })
                // The Processors tab lists the pipeline's processor filters with Add/Edit/Delete
                .story("Processors", context -> render(context, PROCESSOR_FIXTURES))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    // Differs from React: the wire discriminator checks of React's processorFilterApi
                    // (asFilterRow/asProcessorRow) have no GWT code to check; Jackson's 'type' names
                    // are what this story's fixture sends, and the rows below only show if they decode
                    play.click(play.findByText("Processors", StroomDom.LINK_TAB_LABEL));
                    // The filter grid shows the filter (status Complete) and an Add button
                    play.findByText("Complete");
                    play.click(play.getByTitle("Add Processor"));
                    final Play add = dialog(screen, "Add Filter");
                    play.expect(add.getByText("Run As User")).toBeInTheDocument();
                    play.expect(add.getByRole("button", TextMatch.startingWith("Set Feed Dependencies")))
                            .toBeInTheDocument();
                    play.click(add.getByRole("button", StroomDom.button("Cancel")));
                    // Batch Edit -> OK confirms with the number of filters affected
                    play.click(play.getByTitle("Batch Edit Current Processors"));
                    final Play batch = dialog(screen, "Batch Change All Filtered Processors");
                    play.click(batch.getByRole("button", StroomDom.button("OK")));
                    // Differs from React: GWT counts the processor row as well as its filter, so it
                    // asks to 'change 2 processors' rather than 'this processor'
                    final Play confirm = dialogWith(screen,
                            TextMatch.exact("Are you sure you want to change 2 processors?"));
                    play.click(confirm.getByRole("button", StroomDom.button("Cancel")));
                    play.click(batch.getByRole("button", StroomDom.button("Cancel")));
                    // Filter Processors opens its expression dialog
                    play.click(play.getByTitle("Filter Processors"));
                    final Play filter = dialog(screen, "Filter Processors");
                    play.click(filter.getByRole("button", StroomDom.button("Cancel")));
                    // The Info popover shows the filter's details.
                    // Differs from React: GWT's info cell is titled 'Info'
                    play.click(play.getAllByTitle("Info").nth(1));
                    screen.findByText("Total Tasks Created");
                    // The processor row is expanded: collapsing it hides the filter
                    play.click(play.querySelectorAll(".expanderCell .expanderIcon").nth(0));
                    play.waitFor(() -> play.expect(play.queryByText("Complete")).toBeNull());
                    play.click(play.querySelectorAll(".expanderCell .expanderIcon").nth(0));
                    play.findByText("Complete");
                    // Show Tasks is enabled once a single filter row is selected
                    play.expect(play.getByText("Active Tasks", StroomDom.LINK_TAB_LABEL)).toBeInTheDocument();
                    play.click(play.getByText("Complete"));
                    play.waitFor(() -> play.expect(play.getByTitle("Show Tasks")).not().toHaveClass("disabled"));
                    expectNoProblems(play);
                })
                // With data access, the Data tab is there (the default) and Structure is reachable
                .story("WithDataTab", context -> render(context, FIXTURES))
                .withPlay(play -> {
                    // Differs from React: GWT's tabs are link tabs with no 'tab' role
                    play.findByText("Data", StroomDom.LINK_TAB_LABEL);
                    play.findByText("Structure", StroomDom.LINK_TAB_LABEL);
                    openStructure(play);
                    play.findByText("parser");
                    expectNoProblems(play);
                })
                // Changing "Inherit From" rebuilds the base stack from the new parent
                .story("InheritFromRebuild", context -> render(context, INHERIT_FIXTURES))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    openStructure(play);
                    // The initial base is Parent A: the inherited 'Parser A' and the local xslt
                    play.findByText("Parser A");
                    play.findByText("xslt");
                    // Open the Inherit From picker and choose Parent B
                    play.fireEvent().mouseDown(play.querySelector(".dropDownView-container"), EventInit.create());
                    final Play picker = dialog(screen, "Choose item");
                    play.click(picker.findByText("Parent B"));
                    play.click(picker.getByRole("button", StroomDom.button("OK")));
                    // The new parent's layers are fetched and the tree is rebuilt on them
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post(LAYERS_PATH).withJsonBodyContaining("{\"uuid\": \"parent2\"}")
                                    .toSpyMatcher()));
                    play.findByText("Parser B");
                    play.expect(play.queryByText("Parser A")).toBeNull();
                    expectNoProblems(play);
                })
                // Removing an inherited reference keeps it, struck through, and Remove restores it
                .story("ReferenceRemoveRestore", context -> render(context, REF_FIXTURES))
                .withPlay(play -> {
                    openStructure(play);
                    play.click(play.findByText("xslt"));
                    play.findByText("Inherited Ref");
                    // The 'Inherited From' column names the ancestor the reference comes from.
                    // Differs from React: GWT's grid rows are <tr> elements
                    play.expect(play.within(play.getByText("Inherited Ref").closest("tr")).getByText("Parent"))
                            .toBeInTheDocument();
                    // Select the row and Remove it: it stays, struck through (a cell class)
                    play.click(play.getByText("REF_FEED"));
                    play.click(play.getByTitle("Remove Reference"));
                    play.findByText("Inherited Ref");
                    // Differs from React: GWT puts the class on the cell's text, not on the cell
                    play.waitFor(() -> play.expect(play.getByText("REF_FEED"))
                            .toHaveClass("pipelineStructureViewImpl-property-removed"));
                    // Remove again restores it
                    play.click(play.getByText("REF_FEED"));
                    play.click(play.getByTitle("Remove Reference"));
                    play.waitFor(() -> play.expect(play.getByText("REF_FEED"))
                            .toHaveClass("pipelineStructureViewImpl-property-inherited"));
                    expectNoProblems(play);
                })
                // A structure edit dirties the document
                .story("StructureEditDirtiesTheDocument", context -> render(context, FIXTURES))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    openStructure(play);
                    play.findByText("xslt");
                    // Differs from React: the dirty state is a spy on the editor's DirtyEvent (React
                    // shows a 'dirty-probe')
                    play.expect(play.spy(ON_DIRTY)).not().toHaveBeenCalledWith(true);
                    play.click(play.getByText("xslt"));
                    play.click(play.getByTitle("Remove Pipeline Element"));
                    play.click(dialogWith(screen, TextMatch.exact("Are you sure you want to remove this element?"))
                            .getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.queryByText("xslt")).toBeNull());
                    play.waitFor(() -> play.expect(play.spy(ON_DIRTY)).toHaveBeenCalledWith(true));
                    expectNoProblems(play);
                });
    }

    // Differs from React: Stroom's Data tab is the default (for a user who may view data), so the
    // Structure link tab is chosen first
    private static void openStructure(final Play play) {
        play.click(play.findByText("Structure", StroomDom.LINK_TAB_LABEL));
    }

    private static Play dialog(final Play screen, final String caption) {
        return screen.within(screen.findByText(caption, StroomDom.DIALOG_TITLE).closest(StroomDom.DIALOG));
    }

    private static Play dialogWith(final Play screen, final String text) {
        return dialogWith(screen, TextMatch.exact(text));
    }

    private static Play dialogWith(final Play screen, final TextMatch text) {
        return screen.within(screen.findByText(text).closest(StroomDom.DIALOG));
    }

    // A document reference's JSON, as the explorer resource returns it (the fixtures' names need
    // no escaping)
    private static String docRefJson(final Map<?, ?> docRef) {
        return "{\"type\": \"" + docRef.get("type")
               + "\", \"uuid\": \"" + docRef.get("uuid")
               + "\", \"name\": \"" + docRef.get("name") + "\"}";
    }

    private static void expectNoProblems(final Play play) {
        play.expect(play.spy(ScreenHarness.ALERT_SPY)).not().toHaveBeenCalled();
        play.expect(play.spy(ScreenHarness.UNHANDLED_REQUEST_SPY)).not().toHaveBeenCalled();
    }

    private static RestFixtures fixtures(final String doc,
                                         final String streams,
                                         final String processorRows,
                                         final RestFixtures.Builder layers) {
        return layers
                .addAll(TreeFixtures.explorerRoutes(new ExplorerFixture(ExplorerFixture.folder("System",
                                ExplorerFixture.doc("Parent B", PipelineDoc.TYPE).withUuid("parent2"))))
                        // The documents Stroom checks (decorates) for their current names
                        .post(TreeFixtures.DECORATE, request -> RestReply.json(docRefJson(
                                (Map<?, ?>) ((Map<?, ?>) JsonValues.parse(request.getBody())).get("docRef"))))
                        .post("/explorer/v2/fetchDocRefs", request -> RestReply.json(request.getBody()))
                        .get("/pipeline/v1/" + UUID, RestReply.json(doc))
                        .put("/pipeline/v1/" + UUID, request -> RestReply.json(request.getBody()))
                        .get(PipelineFixtures.PROPERTY_TYPES_PATH, RestReply.json(PipelineFixtures.PROPERTY_TYPES))
                        .post("/pipeline/v1/fetchPipelineJson", RestReply.json(
                                "{\"pipeline\": {\"type\": \"Pipeline\", \"uuid\": \"child\", "
                                        + "\"name\": \"My Pipeline\"}, \"json\": \"{ \\\"elements\\\": {} }\"}"))
                        .post("/meta/v1/find", RestReply.json(streams))
                        .post("/processorFilter/v1/find", RestReply.json(processorRows))
                        .post("/expression/v1/validate", RestReply.json("{\"ok\": true}"))
                        .addAll(PipelineFixtures.stepping("xslt"))
                        .route(RequestMatcher.post("/processorTask/v1/*"), RestReply.json(NO_ROWS))
                        .build())
                .build();
    }

    private static Widget render(final StoryContext context, final RestFixtures fixtures) {
        final ProcessingScreenGinjector injector = GWT.create(ProcessingScreenGinjector.class);
        final ScreenHarness harness = ScreenHarness.builder(context, fixtures)
                .injector(injector)
                .realAlerts()
                .build();
        harness.getSecurityContext().setDocumentPermission(DocumentPermission.EDIT);
        final Spy onDirty = harness.fn(ON_DIRTY);
        harness.fn(ON_EMBED);
        // Opened once Stroom has started, as PipelinePlugin opens a document: load it, check the
        // user may edit it, read it into the editor and show the tab
        harness.afterStartUp(() -> {
            final PipelinePresenter presenter = injector.getPipelinePresenter();
            final PipelineResource resource = GWT.create(PipelineResource.class);
            harness.getRestFactory()
                    .create(resource)
                    .method(res -> res.fetch(UUID))
                    .onSuccess(doc -> {
                        harness.addRegistration(presenter.addDirtyHandler(event -> onDirty.call(event.isDirty())));
                        presenter.read(DOC_REF, doc, false);
                        harness.addContent(presenter);
                    })
                    .taskMonitorFactory(presenter)
                    .exec();
        });
        return harness.asWidget();
    }
}
