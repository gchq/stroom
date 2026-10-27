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
import stroom.document.client.event.OpenDocumentEvent;
import stroom.entity.client.component.DocumentEditorHost;
import stroom.gwt.workbench.client.app.gin.processing.ProcessingScreenGinjector;
import stroom.gwt.workbench.client.app.rest.JsonValues;
import stroom.gwt.workbench.client.app.rest.RequestMatcher;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.client.app.screen.StoryDocumentPlugins;
import stroom.gwt.workbench.client.app.screen.StroomDom;
import stroom.gwt.workbench.client.widgets.tree.ExplorerFixture;
import stroom.gwt.workbench.client.widgets.tree.TreeFixtures;
import stroom.gwt.workbench.framework.client.play.EventInit;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.play.TextMatch;
import stroom.gwt.workbench.framework.client.play.ValueMatcher;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.pipeline.client.component.PipelineEditor;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.PipelineResource;
import stroom.security.shared.DocumentPermission;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;

import java.util.Map;

/// Stories of `App/Main/PipelineEditor`, showing Stroom's real [PipelineEditor] (a Pipeline's
/// editor tab: Data, Structure, Processors, Active Tasks, Documentation and Permissions), opened as
/// `PipelinePlugin` opens a document, with fake REST replies.
///
/// | Stroom endpoint | Used for |
/// |---|---|
/// | `GET`, `PUT /pipeline/v1/{uuid}` | fetching and saving the pipeline |
/// | `POST /pipeline/v1/fetchPipelineLayers` (by the pipeline asked for) | its layers |
/// | `GET /pipeline/v1/propertyTypes` | element property types |
/// | `POST /pipeline/v1/fetchPipelineJson` | View Source |
/// | `POST /meta/v1/find` (no streams) | the Data tab's streams |
/// | `POST /processorFilter/v1/find` | the Processors tab |
/// | `POST /processorTask/v1/find`, `POST /processorTask/v1/summary` | the Active Tasks tab |
/// | `POST /explorer/v2/fetchExplorerNodes` | the document pickers (e.g. the Inherit From pipeline) |
/// | `SteppingResource` (`PipelineFixtures.stepping`) | stepping |
/// | `GET`, `PUT /xslt/v1/x1` (through Stroom's `XsltPlugin`) | the other document (`MultiDocumentSave`) |
///
/// `MultiDocumentSave` opens the pipeline through Stroom's document plugins ([StoryDocumentPlugins]):
/// `PipelinePlugin` saves it with its 'Save Pipeline' picker, and `XsltPlugin` loads and saves the
/// code edited while stepping.
public final class PipelineEditorStories {

    /// The name of the spy recording the editor's dirty state (its dirty listener).
    static final String ON_DIRTY = "onDirty";
    // The classes of the box a dragged element would be dropped on, and of one that won't take it
    private static final String HOTSPOT = "pipeline-element--target";
    private static final String REFUSED = "pipeline-element--refused";
    // The class of an element detached with the keyboard, to be attached to another parent
    private static final String DETACHED = "pipeline-element--detached";
    // The tree's tab stop: one box, the selected element's (or the source's when none is)
    private static final String TREE_TAB_STOP = ".pipeline-tree__items [tabindex='0']";
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

    // ComplexStructure: a parent pipeline's straight run, which its child branches three ways
    private static final String COMPLEX_CHILD = """
            {
              "elements": {"add": [
                {"id": "rawWriter", "type": "XMLWriter"}, {"id": "rawAppender", "type": "StreamAppender"},
                {"id": "indexFilter", "type": "XSLTFilter"}, {"id": "indexWriter", "type": "XMLWriter"},
                {"id": "indexAppender", "type": "StreamAppender"},
                {"id": "jsonFilter", "type": "XSLTFilter"}, {"id": "jsonWriter", "type": "XMLWriter"},
                {"id": "jsonAppender", "type": "StreamAppender"}, {"id": "archiveAppender", "type": "StreamAppender"}]},
              "links": {"add": [
                {"from": "parser", "to": "rawWriter"}, {"from": "rawWriter", "to": "rawAppender"},
                {"from": "decoration", "to": "indexFilter"}, {"from": "indexFilter", "to": "indexWriter"},
                {"from": "indexWriter", "to": "indexAppender"},
                {"from": "decoration", "to": "jsonFilter"}, {"from": "jsonFilter", "to": "jsonWriter"},
                {"from": "jsonWriter", "to": "jsonAppender"}, {"from": "jsonWriter", "to": "archiveAppender"}]}
            }""";

    private static final String COMPLEX_DOC = """
            {"type": "Pipeline", "uuid": "child", "name": "Event Pipeline", "version": "v1",
              "parentPipeline": {"type": "Pipeline", "uuid": "parent", "name": "Base Event Pipeline"},
              "pipelineData": COMPLEX_CHILD}""".replace("COMPLEX_CHILD", COMPLEX_CHILD);

    private static final String COMPLEX_LAYERS = """
            [
              {"sourcePipeline": {"type": "Pipeline", "uuid": "parent", "name": "Base Event Pipeline"},
                "pipelineData": {
                  "elements": {"add": [{"id": "parser", "type": "CombinedParser"},
                    {"id": "translation", "type": "XSLTFilter"}, {"id": "decoration", "type": "XSLTFilter"},
                    {"id": "xmlWriter", "type": "XMLWriter"}, {"id": "streamAppender", "type": "StreamAppender"}]},
                  "links": {"add": [{"from": "Source", "to": "parser"}, {"from": "parser", "to": "translation"},
                    {"from": "translation", "to": "decoration"}, {"from": "decoration", "to": "xmlWriter"},
                    {"from": "xmlWriter", "to": "streamAppender"}]}}},
              {"sourcePipeline": {"type": "Pipeline", "uuid": "child", "name": "Event Pipeline"},
                "pipelineData": COMPLEX_CHILD}
            ]""".replace("COMPLEX_CHILD", COMPLEX_CHILD);

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

    // The batch change asks the server how many filters it would change, before confirming it
    private static final String BULK_CHANGE_COUNT_PATH = "/processorFilter/v1/bulkChange/count";
    private static final RestFixtures PROCESSOR_FIXTURES = fixtures(DOC, NO_ROWS, PROCESSOR_ROWS,
            RestFixtures.builder()
                    .post(LAYERS_PATH, RestReply.json(LAYERS))
                    .route(RequestMatcher.post("/processorFilter/v1/find")
                                    .withJsonBodyContaining("{\"expandedRows\": []}"),
                            RestReply.json(COLLAPSED_PROCESSOR_ROWS))
                    .post(BULK_CHANGE_COUNT_PATH, RestReply.json("2")));
    // Creating an embedded XSLT fails, e.g. the server refuses it
    private static final RestFixtures EMBED_FAILS_FIXTURES = fixtures(DOC, NO_ROWS, NO_ROWS,
            RestFixtures.builder()
                    .post(LAYERS_PATH, RestReply.json(LAYERS))
                    .post("/xslt/v1", RestReply.error(500, "Unable to create the XSLT")));
    // As PROCESSOR_FIXTURES, with the batch change failing, e.g. the server refuses it
    private static final RestFixtures BATCH_FAILS_FIXTURES = fixtures(DOC, NO_ROWS, PROCESSOR_ROWS,
            RestFixtures.builder()
                    .post(LAYERS_PATH, RestReply.json(LAYERS))
                    .route(RequestMatcher.post("/processorFilter/v1/find")
                                    .withJsonBodyContaining("{\"expandedRows\": []}"),
                            RestReply.json(COLLAPSED_PROCESSOR_ROWS))
                    .post(BULK_CHANGE_COUNT_PATH, RestReply.json("2"))
                    .post("/processorFilter/v1/bulkChange", RestReply.error(500, "Unable to change the processors")));
    private static final RestFixtures STEPPING_FIXTURES = fixtures(DOC, STREAMS, NO_ROWS,
            RestFixtures.builder().post(LAYERS_PATH, RestReply.json(LAYERS)));
    // The XSLT of the xslt element, whose code is edited while stepping
    private static final String XSLT_PATH = "/xslt/v1/x1";
    private static final String XSLT = """
            {"type": "XSLT", "uuid": "x1", "name": "My XSLT", "data": "ORIGINAL XSLT CODE"}""";

    // Stepping, with the xslt element's code in 'My XSLT' (loaded and saved by its plugin)
    private static final RestFixtures MULTI_SAVE_FIXTURES = fixtures(DOC, STREAMS, NO_ROWS,
            StoryDocumentPlugins.documentRoutes(RestFixtures.builder()
                            .post(LAYERS_PATH, RestReply.json(LAYERS))
                            .post("/stepping/v1/findElementDoc", RestReply.json(
                                    "{\"type\": \"XSLT\", \"uuid\": \"x1\", \"name\": \"My XSLT\"}")),
                    XSLT_PATH, XSLT));
    private static final RestFixtures INHERIT_FIXTURES = fixtures(INHERIT_DOC, NO_ROWS, NO_ROWS,
            RestFixtures.builder()
                    .route(RequestMatcher.post(LAYERS_PATH).withJsonBodyContaining("{\"uuid\": \"parent2\"}"),
                            RestReply.json("[" + BASE_B + "]"))
                    .post(LAYERS_PATH, RestReply.json(INHERIT_LAYERS)));
    private static final RestFixtures REF_FIXTURES = fixtures(REF_DOC, NO_ROWS, NO_ROWS,
            RestFixtures.builder().post(LAYERS_PATH, RestReply.json(REF_LAYERS)));
    private static final RestFixtures COMPLEX_FIXTURES = fixtures(COMPLEX_DOC, STREAMS, NO_ROWS,
            RestFixtures.builder().post(LAYERS_PATH, RestReply.json(COMPLEX_LAYERS)));

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
                // A larger pipeline, with no play so that it can be looked at: a parent's straight run
                // of 6 elements, which the child branches three ways (15 elements in all)
                .story("ComplexStructure", context -> render(context, COMPLEX_FIXTURES))
                // Moving round the tree with the keyboard: it is one tab stop, Left goes to an element's
                // parent and Right to its first child, Up and Down to its siblings and Home and End to
                // the first and last of them, stopping at the ends; the selection follows the focus
                .story("KeyboardNavigation", context -> render(context, COMPLEX_FIXTURES))
                .withPlay(play -> {
                    openStructure(play);
                    play.findByText("rawWriter");
                    final Play tree = play.within(play.querySelector(".pipeline-tree__items"));
                    play.expect(play.getByRole("tree", "Pipeline structure")).toBeInTheDocument();
                    play.expect(play.querySelectorAll(TREE_TAB_STOP)).toHaveLength(1);

                    // Clicking Source focuses it; Right twice (pressed quickly) goes to the parser's
                    // first child
                    play.click(elementBox(tree, "Source"));
                    play.waitFor(() -> play.expect(elementBox(tree, "Source")).toHaveFocus());
                    play.keyboard("{ArrowRight}{ArrowRight}");
                    play.waitFor(() -> play.expect(elementBox(tree, "rawWriter")).toHaveFocus());
                    final Query rawWriter = elementBox(tree, "rawWriter");
                    play.expect(rawWriter).toHaveAttribute("role", "treeitem");
                    play.expect(rawWriter).toHaveAttribute("aria-label", "rawWriter, XML Writer");
                    play.expect(rawWriter).toHaveAttribute("aria-level", "3");
                    play.expect(rawWriter).toHaveAttribute("aria-posinset", "1");
                    play.expect(rawWriter).toHaveAttribute("aria-setsize", "2");
                    play.expect(rawWriter).toHaveAttribute("aria-selected", "true");
                    play.expect(rawWriter).toHaveAttribute("tabindex", "0");
                    play.expect(play.querySelectorAll(TREE_TAB_STOP)).toHaveLength(1);
                    play.expect(elementBox(tree, "Source")).toHaveAttribute("aria-selected", "false");

                    // Up and Down go to its siblings, stopping at the first and last
                    play.keyboard("{ArrowUp}");
                    play.expect(elementBox(tree, "rawWriter")).toHaveFocus();
                    play.keyboard("{ArrowDown}");
                    play.waitFor(() -> play.expect(elementBox(tree, "translation")).toHaveFocus());
                    play.expect(elementBox(tree, "translation")).toHaveAttribute("aria-posinset", "2");
                    play.keyboard("{ArrowDown}");
                    play.expect(elementBox(tree, "translation")).toHaveFocus();
                    play.keyboard("{ArrowUp}");
                    play.waitFor(() -> play.expect(elementBox(tree, "rawWriter")).toHaveFocus());

                    // Home and End go to the first and last of decoration's three children
                    play.keyboard("{ArrowDown}{ArrowRight}{ArrowRight}");
                    play.waitFor(() -> play.expect(elementBox(tree, "indexFilter")).toHaveFocus());
                    play.keyboard("{End}");
                    play.waitFor(() -> play.expect(elementBox(tree, "xmlWriter")).toHaveFocus());
                    play.expect(elementBox(tree, "xmlWriter")).toHaveAttribute("aria-posinset", "3");
                    play.expect(elementBox(tree, "xmlWriter")).toHaveAttribute("aria-setsize", "3");
                    play.keyboard("{Home}");
                    play.waitFor(() -> play.expect(elementBox(tree, "indexFilter")).toHaveFocus());

                    // Right stops at an element with no children
                    play.keyboard("{ArrowRight}{ArrowRight}");
                    play.waitFor(() -> play.expect(elementBox(tree, "indexAppender")).toHaveFocus());
                    play.keyboard("{ArrowRight}");
                    play.expect(elementBox(tree, "indexAppender")).toHaveFocus();

                    // Left goes back to the parents, stopping at the source
                    play.keyboard("{ArrowLeft}{ArrowLeft}{ArrowLeft}{ArrowLeft}{ArrowLeft}{ArrowLeft}");
                    play.waitFor(() -> play.expect(elementBox(tree, "Source")).toHaveFocus());
                    play.expect(elementBox(tree, "Source")).toHaveAttribute("aria-level", "1");
                    play.keyboard("{ArrowLeft}{ArrowUp}{ArrowDown}");
                    play.expect(elementBox(tree, "Source")).toHaveFocus();
                    play.expect(elementBox(tree, "Source")).toHaveAttribute("aria-selected", "true");

                    // Tab leaves the tree, and Shift+Tab comes back to the selected element
                    play.keyboard("{ArrowRight}");
                    play.waitFor(() -> play.expect(elementBox(tree, "parser")).toHaveFocus());
                    play.keyboard("{Tab}");
                    play.expect(play.querySelector(".pipeline-tree__items :focus")).toBeNull();
                    play.keyboard("{Shift>}{Tab}{/Shift}");
                    play.expect(elementBox(tree, "parser")).toHaveFocus();
                    expectNoProblems(play);
                })
                // Moving an element (with its children) with the keyboard: Ctrl+X detaches it, the
                // element focused shows whether it would take it (green or red, as when dragging),
                // Ctrl+V attaches it there and Escape puts it back
                .story("KeyboardMove", context -> render(context, COMPLEX_FIXTURES))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    openStructure(play);
                    play.findByText("rawWriter");
                    final Play tree = play.within(play.querySelector(".pipeline-tree__items"));
                    final Query status = play.querySelector(".draggable-tree__status");
                    play.click(elementBox(tree, "rawWriter"));
                    play.waitFor(() -> play.expect(elementBox(tree, "rawWriter")).toHaveFocus());

                    // The source can't be detached
                    play.keyboard("{ArrowLeft}{ArrowLeft}");
                    play.waitFor(() -> play.expect(elementBox(tree, "Source")).toHaveFocus());
                    play.keyboard("{Control>}x{/Control}");
                    play.expect(play.querySelectorAll("." + DETACHED)).toHaveLength(0);
                    play.keyboard("{ArrowRight}{ArrowRight}");
                    play.waitFor(() -> play.expect(elementBox(tree, "rawWriter")).toHaveFocus());

                    // Ctrl+X detaches rawWriter and rawAppender, which stay where they are, faded
                    play.keyboard("{Control>}x{/Control}");
                    play.waitFor(() -> play.expect(elementBox(tree, "rawWriter")).toHaveClass(DETACHED));
                    play.expect(elementBox(tree, "rawAppender")).toHaveClass(DETACHED);
                    play.expect(elementBox(tree, "translation")).not().toHaveClass(DETACHED);
                    play.expect(elementBox(tree, "rawWriter")).toHaveAttribute("aria-label",
                            "rawWriter, XML Writer, detached");
                    play.waitFor(() -> play.expect(status).toHaveTextContent(
                            TextMatch.startingWith("rawWriter and the 1 element after it detached")));

                    // Ctrl+V on its own parent leaves it there
                    play.keyboard("{ArrowLeft}");
                    play.waitFor(() -> play.expect(elementBox(tree, "parser")).toHaveFocus());
                    play.expect(elementBox(tree, "parser")).toHaveClass(HOTSPOT);
                    play.keyboard("{Control>}v{/Control}");
                    play.waitFor(() -> play.expect(status).toHaveTextContent("rawWriter left under parser."));
                    play.waitFor(() -> play.expect(elementBox(tree, "rawWriter")).toHaveFocus());
                    play.expect(play.querySelectorAll("." + HOTSPOT + ", ." + REFUSED + ", ." + DETACHED))
                            .toHaveLength(0);
                    play.expect(play.spy(ON_DIRTY)).not().toHaveBeenCalledWith(true);

                    // Moving on shows whether each element would take it; it can't go under itself
                    play.keyboard("{Control>}x{/Control}");
                    play.waitFor(() -> play.expect(elementBox(tree, "rawWriter")).toHaveClass(DETACHED));
                    play.expect(elementBox(tree, "rawWriter")).not().toHaveClass(HOTSPOT);
                    play.expect(elementBox(tree, "rawWriter")).not().toHaveClass(REFUSED);
                    play.keyboard("{ArrowDown}{ArrowRight}");
                    play.waitFor(() -> play.expect(elementBox(tree, "decoration")).toHaveFocus());
                    play.waitFor(() -> play.expect(elementBox(tree, "decoration")).toHaveClass(HOTSPOT));
                    // Each element's name says whether it can take it, before trying
                    play.expect(elementBox(tree, "decoration")).toHaveAttribute("aria-label",
                            "decoration, XSLT Filter, can take rawWriter");
                    play.expect(refusedIcon(tree, "decoration")).toBeNull();
                    play.keyboard("{ArrowRight}{ArrowRight}");
                    play.waitFor(() -> play.expect(elementBox(tree, "indexWriter")).toHaveFocus());
                    play.waitFor(() -> play.expect(elementBox(tree, "indexWriter")).toHaveClass(REFUSED));
                    play.expect(elementBox(tree, "decoration")).not().toHaveClass(HOTSPOT);
                    // It refuses it: not by colour alone, its type icon is the "not allowed" icon
                    play.expect(elementBox(tree, "indexWriter")).toHaveAttribute("aria-label",
                            "indexWriter, XML Writer, can't take rawWriter");
                    play.expect(refusedIcon(tree, "indexWriter")).toBeVisible();
                    play.expect(typeIcon(tree, "indexWriter")).toBeNull();
                    // A writer can't take another: Ctrl+V says so and keeps it detached
                    play.keyboard("{Control>}v{/Control}");
                    play.waitFor(() -> play.expect(status).toHaveTextContent("indexWriter can't take rawWriter."));
                    play.expect(elementBox(tree, "rawWriter")).toHaveClass(DETACHED);

                    // Ctrl+V on decoration attaches it there, keeping the focus on it
                    play.keyboard("{ArrowLeft}{ArrowLeft}");
                    play.waitFor(() -> play.expect(elementBox(tree, "decoration")).toHaveFocus());
                    play.keyboard("{Control>}v{/Control}");
                    play.waitFor(() -> play.expect(status).toHaveTextContent("rawWriter attached to decoration."));
                    play.waitFor(() -> play.expect(elementBox(tree, "rawWriter")).toHaveFocus());
                    play.expect(elementBox(tree, "indexWriter"))
                            .toHaveAttribute("aria-label", "indexWriter, XML Writer");
                    play.expect(typeIcon(tree, "indexWriter")).toBeVisible();
                    final Query movedWriter = elementBox(tree, "rawWriter");
                    final Query xmlWriter = elementBox(tree, "xmlWriter");
                    play.waitFor(() -> play.expect("rawWriter's left less xmlWriter's",
                            () -> movedWriter.left().get() - xmlWriter.left().get()).toBe(0.0));
                    play.expect(elementBox(tree, "rawWriter")).toHaveAttribute("aria-level", "5");
                    play.expect(play.querySelectorAll("." + HOTSPOT + ", ." + REFUSED + ", ." + DETACHED))
                            .toHaveLength(0);
                    play.waitFor(() -> play.expect(play.spy(ON_DIRTY)).toHaveBeenCalledWith(true));

                    // Escape puts a detached element back
                    play.keyboard("{Control>}x{/Control}");
                    play.waitFor(() -> play.expect(elementBox(tree, "rawWriter")).toHaveClass(DETACHED));
                    play.keyboard("{Escape}");
                    play.waitFor(() -> play.expect(status).toHaveTextContent("rawWriter put back."));
                    play.expect(elementBox(tree, "rawWriter")).not().toHaveClass(DETACHED);
                    play.expect(elementBox(tree, "rawWriter")).toHaveFocus();

                    // Delete asks to remove the focused element
                    play.keyboard("{Delete}");
                    play.click(dialogWith(screen, TextMatch.exact("Are you sure you want to remove this element?"))
                            .getByRole("button", StroomDom.button("Cancel")));
                    expectNoProblems(play);
                })
                // The keyboard's moves are in the element's context menu too, which Shift+F10 or the
                // context menu key opens: Detach, and while an element is detached "Attach '…' here"
                // (only where it would be taken) and "Put '…' back"
                .story("KeyboardContextMenu", context -> render(context, COMPLEX_FIXTURES))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    openStructure(play);
                    play.findByText("rawWriter");
                    final Play tree = play.within(play.querySelector(".pipeline-tree__items"));
                    final Query status = play.querySelector(".draggable-tree__status");
                    play.click(elementBox(tree, "rawWriter"));
                    play.waitFor(() -> play.expect(elementBox(tree, "rawWriter")).toHaveFocus());

                    // Shift+F10 opens rawWriter's menu; Detach detaches it
                    play.keyboard("{Shift>}{F10}{/Shift}");
                    play.click(screen.findByRole("menuitem", TextMatch.containing("Detach")));
                    play.waitFor(() -> play.expect(elementBox(tree, "rawWriter")).toHaveClass(DETACHED));
                    play.expect(elementBox(tree, "rawAppender")).toHaveClass(DETACHED);

                    // An element that won't take it can't have it attached
                    play.click(elementBox(tree, "indexWriter"));
                    play.waitFor(() -> play.expect(elementBox(tree, "indexWriter")).toHaveFocus());
                    play.keyboard("{Shift>}{F10}{/Shift}");
                    play.expect(screen.findByRole("menuitem", TextMatch.containing("Attach 'rawWriter' here")))
                            .toHaveAttribute("aria-disabled", "true");
                    play.expect(screen.getByRole("menuitem", TextMatch.containing("Put 'rawWriter' back")))
                            .toBeInTheDocument();
                    play.expect(screen.queryByRole("menuitem", TextMatch.containing("Detach"))).toBeNull();
                    play.keyboard("{Escape}");
                    play.expect(elementBox(tree, "rawWriter")).toHaveClass(DETACHED);

                    // decoration takes it: the context menu key's menu attaches it there
                    play.click(elementBox(tree, "decoration"));
                    play.waitFor(() -> play.expect(elementBox(tree, "decoration")).toHaveFocus());
                    play.keyboard("{ContextMenu}");
                    play.click(screen.findByRole("menuitem", TextMatch.containing("Attach 'rawWriter' here")));
                    play.waitFor(() -> play.expect(status).toHaveTextContent("rawWriter attached to decoration."));
                    play.waitFor(() -> play.expect(elementBox(tree, "rawWriter")).toHaveAttribute("aria-level", "5"));
                    play.expect(play.querySelectorAll("." + DETACHED)).toHaveLength(0);
                    play.waitFor(() -> play.expect(play.spy(ON_DIRTY)).toHaveBeenCalledWith(true));

                    // "Put 'rawWriter' back" puts it back
                    play.waitFor(() -> play.expect(elementBox(tree, "rawWriter")).toHaveFocus());
                    play.keyboard("{Control>}x{/Control}");
                    play.waitFor(() -> play.expect(elementBox(tree, "rawWriter")).toHaveClass(DETACHED));
                    play.keyboard("{Shift>}{F10}{/Shift}");
                    play.click(screen.findByRole("menuitem", TextMatch.containing("Put 'rawWriter' back")));
                    play.waitFor(() -> play.expect(status).toHaveTextContent("rawWriter put back."));
                    play.expect(play.querySelectorAll("." + DETACHED)).toHaveLength(0);
                    expectNoProblems(play);
                })
                // A right-click on an element that isn't selected selects it and opens its own menu,
                // not the previously selected element's (#2748)
                .story("RightClickMenu", context -> render(context, COMPLEX_FIXTURES))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    openStructure(play);
                    play.findByText("rawWriter");
                    final Play tree = play.within(play.querySelector(".pipeline-tree__items"));
                    play.click(elementBox(tree, "rawWriter"));
                    play.waitFor(() -> play.expect(elementBox(tree, "rawWriter")).toHaveFocus());

                    rightClick(play, elementBox(tree, "xmlWriter"));
                    play.expect(screen.findByRole("menu", "Actions for element 'xmlWriter'"))
                            .toBeVisible();
                    play.keyboard("{Escape}");

                    rightClick(play, elementBox(tree, "indexWriter"));
                    play.expect(screen.findByRole("menu", "Actions for element 'indexWriter'"))
                            .toBeVisible();
                    play.keyboard("{Escape}");
                    expectNoProblems(play);
                })
                // Dragging while an element is detached with the keyboard puts it back first
                .story("DragCancelsKeyboardMove", context -> render(context, COMPLEX_FIXTURES))
                .withPlay(play -> {
                    openStructure(play);
                    play.findByText("rawWriter");
                    final Play tree = play.within(play.querySelector(".pipeline-tree__items"));
                    final Query status = play.querySelector(".draggable-tree__status");
                    play.click(elementBox(tree, "rawWriter"));
                    play.waitFor(() -> play.expect(elementBox(tree, "rawWriter")).toHaveFocus());
                    play.keyboard("{Control>}x{/Control}");
                    play.waitFor(() -> play.expect(elementBox(tree, "rawWriter")).toHaveClass(DETACHED));

                    // Dragging xmlWriter puts rawWriter back, and the drag shows its own target
                    final Query xmlWriter = elementBox(tree, "xmlWriter");
                    play.fireEvent().mouseDown(xmlWriter, EventInit.create().atCentreOf(xmlWriter).buttons(1));
                    play.fireEvent().mouseMove(xmlWriter, EventInit.create().relativeTo(xmlWriter, 60, 40).buttons(1));
                    play.waitFor(() -> play.expect(tree.queryByText("xmlWriter")).toBeNull());
                    play.waitFor(() -> play.expect(status).toHaveTextContent("rawWriter put back."));
                    play.expect(play.querySelectorAll("." + DETACHED)).toHaveLength(0);

                    // Dropping it on the parser moves it there
                    final Query parser = elementBox(tree, "parser");
                    play.fireEvent().mouseMove(parser, EventInit.create().atCentreOf(parser).buttons(1));
                    play.waitFor(() -> play.expect(elementBox(tree, "parser")).toHaveClass(HOTSPOT));
                    play.fireEvent().mouseUp(parser, EventInit.create().atCentreOf(parser));
                    play.waitFor(() -> play.expect(elementBox(tree, "xmlWriter")).toHaveAttribute("aria-level", "3"));
                    play.expect(play.querySelectorAll("." + HOTSPOT + ", ." + REFUSED + ", ." + DETACHED))
                            .toHaveLength(0);
                    expectNoProblems(play);
                })
                // Dragging an element (with its children) to a new parent: the element under the
                // pointer has a green border if it would take it, or a red one (and the "not allowed"
                // icon) if not, and nothing else is marked; dropping on a green one moves it
                .story("DragToMove", context -> render(context, COMPLEX_FIXTURES))
                .withPlay(play -> {
                    openStructure(play);
                    play.findByText("rawWriter");
                    play.expect(play.spy(ON_DIRTY)).not().toHaveBeenCalledWith(true);
                    // The tree's boxes, not those of the dragged copy (a second tree panel)
                    final Play tree = play.within(play.querySelector(".pipeline-tree__items"));

                    // Pressing on rawWriter and moving past the drag threshold lifts it (and
                    // rawAppender) out of the tree
                    final Query rawWriter = elementBox(tree, "rawWriter");
                    play.fireEvent().mouseDown(rawWriter, EventInit.create().atCentreOf(rawWriter).buttons(1));
                    play.fireEvent().mouseMove(rawWriter, EventInit.create().relativeTo(rawWriter, 60, 40).buttons(1));
                    play.waitFor(() -> play.expect(tree.queryByText("rawWriter")).toBeNull());

                    // An appender can't have children: it refuses it, and is the only one marked
                    final Query streamAppender = elementBox(tree, "streamAppender");
                    play.fireEvent().mouseMove(streamAppender,
                            EventInit.create().atCentreOf(streamAppender).buttons(1));
                    play.waitFor(() -> play.expect(elementBox(tree, "streamAppender")).toHaveClass(REFUSED));
                    play.expect(refusedIcon(tree, "streamAppender")).toBeVisible();
                    play.expect(play.querySelectorAll("." + HOTSPOT)).toHaveLength(0);

                    // decoration takes it: it is the target, and the appender no longer refuses
                    final Query decoration = elementBox(tree, "decoration");
                    play.fireEvent().mouseMove(decoration, EventInit.create().atCentreOf(decoration).buttons(1));
                    play.waitFor(() -> play.expect(elementBox(tree, "decoration")).toHaveClass(HOTSPOT));
                    play.expect(elementBox(tree, "streamAppender")).not().toHaveClass(REFUSED);
                    play.expect(refusedIcon(tree, "streamAppender")).toBeNull();
                    play.expect(typeIcon(tree, "streamAppender")).toBeVisible();
                    // The copy of what is dragged floats over the tree, faded, once it has moved
                    final Query copy = play.querySelector(".draggable-tree__dragged");
                    play.expect(copy).toHaveStyle("position", "absolute");
                    play.expect(copy).toHaveStyle("opacity", "0.5");

                    // Over nothing, nothing is marked; back over decoration, it is again
                    play.fireEvent().mouseMove(streamAppender,
                            EventInit.create().relativeTo(streamAppender, 0, 120).buttons(1));
                    play.waitFor(() -> play.expect(play.querySelectorAll("." + HOTSPOT + ", ." + REFUSED))
                            .toHaveLength(0));
                    play.fireEvent().mouseMove(decoration, EventInit.create().atCentreOf(decoration).buttons(1));
                    play.waitFor(() -> play.expect(elementBox(tree, "decoration")).toHaveClass(HOTSPOT));

                    // Dropping it there moves it into decoration's children's column, as wide as them
                    play.fireEvent().mouseUp(decoration, EventInit.create().atCentreOf(decoration));
                    play.waitFor(() -> play.expect(tree.queryByText("rawWriter")).toBeInTheDocument());
                    final Query movedWriter = elementBox(tree, "rawWriter");
                    final Query xmlWriter = elementBox(tree, "xmlWriter");
                    play.waitFor(() -> play.expect("rawWriter's left less xmlWriter's",
                            () -> movedWriter.left().get() - xmlWriter.left().get()).toBe(0.0));
                    play.expect("rawWriter's width less xmlWriter's",
                            () -> movedWriter.width().get() - xmlWriter.width().get()).toBe(0.0);
                    play.expect(play.querySelectorAll("." + HOTSPOT + ", ." + REFUSED)).toHaveLength(0);
                    play.waitFor(() -> play.expect(play.spy(ON_DIRTY)).toHaveBeenCalledWith(true));
                    expectNoProblems(play);
                })
                // A drag that isn't dropped on an element that takes it puts what is dragged back where
                // it was, among its siblings as it was, changing nothing: released over nothing, over
                // an element that refuses it or over where it came from, or with Escape pressed
                .story("DragToCancel", context -> render(context, COMPLEX_FIXTURES))
                .withPlay(play -> {
                    openStructure(play);
                    play.findByText("rawWriter");
                    final Play tree = play.within(play.querySelector(".pipeline-tree__items"));
                    final Query streamAppender = elementBox(tree, "streamAppender");
                    final Query decoration = elementBox(tree, "decoration");
                    final Query parser = elementBox(tree, "parser");

                    // Released over nothing
                    liftRawWriter(play, tree);
                    play.fireEvent().mouseMove(streamAppender,
                            EventInit.create().relativeTo(streamAppender, 0, 120).buttons(1));
                    play.fireEvent().mouseUp(streamAppender, EventInit.create().relativeTo(streamAppender, 0, 120));
                    expectRawWriterBack(play, tree);

                    // Released over an element that refuses it
                    liftRawWriter(play, tree);
                    play.fireEvent().mouseMove(streamAppender,
                            EventInit.create().atCentreOf(streamAppender).buttons(1));
                    play.waitFor(() -> play.expect(elementBox(tree, "streamAppender")).toHaveClass(REFUSED));
                    play.fireEvent().mouseUp(streamAppender, EventInit.create().atCentreOf(streamAppender));
                    expectRawWriterBack(play, tree);

                    // Released over where it came from (it stays first of the parser's children)
                    liftRawWriter(play, tree);
                    play.fireEvent().mouseMove(parser, EventInit.create().atCentreOf(parser).buttons(1));
                    play.waitFor(() -> play.expect(elementBox(tree, "parser")).toHaveClass(HOTSPOT));
                    play.fireEvent().mouseUp(parser, EventInit.create().atCentreOf(parser));
                    expectRawWriterBack(play, tree);

                    // Escape over an element that would take it
                    liftRawWriter(play, tree);
                    play.fireEvent().mouseMove(decoration, EventInit.create().atCentreOf(decoration).buttons(1));
                    play.waitFor(() -> play.expect(elementBox(tree, "decoration")).toHaveClass(HOTSPOT));
                    play.keyboard("{Escape}");
                    expectRawWriterBack(play, tree);
                    // Releasing after Escape doesn't drop it
                    play.fireEvent().mouseUp(decoration, EventInit.create().atCentreOf(decoration));
                    expectRawWriterBack(play, tree);

                    play.expect(play.spy(ON_DIRTY)).not().toHaveBeenCalledWith(true);
                    expectNoProblems(play);
                })
                // A tree too wide for its pane: moving to an element off screen with the keyboard
                // scrolls it into view, pressing on the background and dragging pans it, and
                // dragging an element to the pane's edge scrolls it, so that an element off screen can
                // take it
                .story("ScrollAndPan", context -> render(context, COMPLEX_FIXTURES))
                .withPlay(play -> {
                    openStructure(play);
                    play.findByText("rawWriter");
                    final Play tree = play.within(play.querySelector(".pipeline-tree__items"));
                    final Query boxPanel = play.querySelector(".pipeline-tree__items");
                    final Query pane = play.querySelector(".pipeline-structure__tree-container");
                    play.run("narrow the tree's pane, so that it scrolls",
                            () -> pane.element().get().getStyle().setProperty("width", "360px"));
                    play.waitFor(() -> play.expect("the pane's scrollable width",
                            () -> scrollLeftMax(pane)).toBeGreaterThan(100));

                    // Moving to indexWriter, off screen, scrolls all of it into view
                    play.click(elementBox(tree, "Source"));
                    play.keyboard("{ArrowRight}{ArrowRight}{ArrowDown}{ArrowRight}{ArrowRight}{ArrowRight}");
                    play.waitFor(() -> play.expect(elementBox(tree, "indexWriter")).toHaveFocus());
                    final Query indexWriter = elementBox(tree, "indexWriter");
                    play.waitFor(() -> play.expect("indexWriter's right beyond the pane's",
                            () -> indexWriter.right().get() - pane.right().get()).toBeLessThanOrEqual(0));

                    // Pressing on the background and dragging pans it, and selects nothing
                    play.run("scroll the pane back", () -> pane.element().get().setScrollLeft(0));
                    play.click(elementBox(tree, "Source"));
                    play.fireEvent().mouseDown(boxPanel, EventInit.create().relativeTo(pane, 300, 12).buttons(1));
                    play.fireEvent().mouseMove(boxPanel, EventInit.create().relativeTo(pane, 225, 12).buttons(1));
                    play.fireEvent().mouseMove(boxPanel, EventInit.create().relativeTo(pane, 150, 12).buttons(1));
                    play.fireEvent().mouseUp(boxPanel, EventInit.create().relativeTo(pane, 150, 12));
                    play.expect("the pane's scroll", () -> scrollLeft(pane)).toBe(150.0);
                    play.expect(elementBox(tree, "Source")).toHaveAttribute("aria-selected", "true");

                    // Dragging rawWriter to the pane's right edge scrolls it, as far as the tree goes
                    play.run("scroll the pane back", () -> pane.element().get().setScrollLeft(0));
                    final Query rawWriter = elementBox(tree, "rawWriter");
                    play.fireEvent().mouseDown(rawWriter, EventInit.create().atCentreOf(rawWriter).buttons(1));
                    play.fireEvent().mouseMove(rawWriter, EventInit.create().relativeTo(rawWriter, 60, 40).buttons(1));
                    play.waitFor(() -> play.expect(tree.queryByText("rawWriter")).toBeNull());
                    play.fireEvent().mouseMove(boxPanel, EventInit.create().relativeTo(pane, 355, 60).buttons(1));
                    play.waitFor(() -> play.expect("the pane's scroll", () -> scrollLeft(pane))
                            .toBeGreaterThan(200));
                    // jsonFilter, off screen before, takes it
                    final Query jsonFilter = elementBox(tree, "jsonFilter");
                    play.fireEvent().mouseMove(jsonFilter, EventInit.create().atCentreOf(jsonFilter).buttons(1));
                    play.waitFor(() -> play.expect(elementBox(tree, "jsonFilter")).toHaveClass(HOTSPOT));
                    play.fireEvent().mouseUp(jsonFilter, EventInit.create().atCentreOf(jsonFilter));
                    play.waitFor(() -> play.expect(tree.queryByText("rawWriter")).toBeInTheDocument());
                    play.waitFor(() -> play.expect(elementBox(tree, "rawWriter")).toHaveAttribute("aria-level", "6"));
                    play.waitFor(() -> play.expect(play.spy(ON_DIRTY)).toHaveBeenCalledWith(true));
                    expectNoProblems(play);
                })
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
                    // GWT shows the element id and name as text, not as fields
                    play.expect(property.getAllByText("xslt").count()).toBeGreaterThan(0);
                    play.click(property.getByRole("button", StroomDom.button("Cancel")));
                    play.waitFor(() -> play.expect(screen.queryByText("Edit Property", StroomDom.DIALOG_TITLE))
                            .toBeNull());
                    // The reference loader grid shows the element's references, with a New button
                    play.expect(play.getByText("Ref Pipeline")).toBeInTheDocument();
                    play.expect(play.getByText("MY_FEED")).toBeInTheDocument();
                    play.expect(play.getByTitle("New Pipeline Reference")).toBeInTheDocument();
                    // View Source opens the pipeline's JSON, captioned 'Pipeline Source'. It is
                    // a toolbar button (it was a GWT Hyperlink, which added a browser history
                    // entry)
                    play.click(play.getByRole("button", "View Source"));
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
                // A boolean property is chosen from true and false; choosing one makes its Source
                // Local, and OK sets it on the element
                .story("BooleanProperty", context -> render(context, FIXTURES))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    openStructure(play);
                    play.click(play.findByText("parser"));
                    play.dblClick(play.findByText("fixInvalidChars"));
                    final Play property = dialog(screen, "Edit Property");
                    play.expect(property.getByText("Fix invalid XML characters")).toBeInTheDocument();
                    final Query value = property.getByRole("combobox", "Value");
                    play.expect(value).toHaveValue("false");
                    play.click(value);
                    play.click(screen.findByText("true", ".selection-popup *"));
                    play.waitFor(() -> play.expect(property.getByRole("combobox", "Source")).toHaveValue("Local"));
                    play.click(property.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(screen.queryByText("Edit Property", StroomDom.DIALOG_TITLE))
                            .toBeNull());
                    play.waitFor(() -> play.expect(play.getByText("true")).toBeInTheDocument());
                    expectNoProblems(play);
                })
                // A changed value makes the Source Local; choosing Default again then shows the
                // default value (the changed value stayed, under Default, and choosing Local by
                // hand threw the change away)
                .story("PropertySourceAfterChange", context -> render(context, FIXTURES))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    openStructure(play);
                    play.click(play.findByText("parser"));
                    play.dblClick(play.findByText("fixInvalidChars"));
                    final Play property = dialog(screen, "Edit Property");
                    final Query value = property.getByRole("combobox", "Value");
                    final Query source = property.getByRole("combobox", "Source");
                    play.click(value);
                    play.click(screen.findByText("true", ".selection-popup *"));
                    play.waitFor(() -> play.expect(source).toHaveValue("Local"));
                    play.click(source);
                    play.click(screen.findByText("Default", ".selection-popup *"));
                    play.waitFor(() -> play.expect(source).toHaveValue("Default"));
                    play.waitFor(() -> play.expect(value).toHaveValue("false"));
                    play.click(property.getByRole("button", StroomDom.button("Cancel")));
                    play.waitFor(() -> play.expect(screen.queryByText("Edit Property", StroomDom.DIALOG_TITLE))
                            .toBeNull());
                    // Another element, as a default value on the property grid's selected row fails
                    // contrast (see ISSUES.md)
                    play.click(play.getByText("Source"));
                    play.waitFor(() -> play.expect(play.queryByText("fixInvalidChars")).toBeNull());
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
                    // OK creates the embedded XSLT with the XSLT DocumentPlugin
                    // (PropertyListController.createEmbeddedDocument), which this story doesn't register
                    // (see StoryDocumentPlugins), so the story stops before OK
                    play.expect(play.spy(ScreenHarness.UNHANDLED_REQUEST_SPY)).not().toHaveBeenCalled();
                })
                // The Structure tab toggles into stepping mode with a stream list
                .story("SteppingMode", context -> render(context, STEPPING_FIXTURES))
                .withPlay(play -> {
                    openStructure(play);
                    play.findByText("parser");
                    play.click(play.findByTitle("Stepping Mode"));
                    // The stream list shows; selecting a stream mounts the stepper.
                    // The stream list is a grid of streams
                    play.click(play.findAllByText("MY_FEED").nth(0));
                    play.findByTitle("Step Forward");
                    expectNoProblems(play);
                })
                // The Processors tab lists the pipeline's processor filters with Add/Edit/Delete
                .story("Processors", context -> render(context, PROCESSOR_FIXTURES))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    // The fixture sends Jackson's 'type' names, and the rows below only show if they
                    // decode
                    play.click(play.findByText("Processors", StroomDom.LINK_TAB_LABEL));
                    // The filter grid shows the filter (status Complete) and an Add button
                    play.findByText("Complete");
                    play.click(play.getByTitle("New Processor"));
                    final Play add = dialog(screen, "Add Filter");
                    play.expect(add.getByText("Run As User")).toBeInTheDocument();
                    play.expect(add.getByRole("button", TextMatch.startingWith("Set Feed Dependencies")))
                            .toBeInTheDocument();
                    play.click(add.getByRole("button", StroomDom.button("Cancel")));
                    // Batch Edit -> OK confirms with the number of filters affected
                    play.click(play.getByTitle("Batch Edit Filtered Processors"));
                    final Play batch = dialog(screen, "Batch Change All Filtered Processors");
                    play.click(batch.getByRole("button", StroomDom.button("OK")));
                    // GWT counts the processor row as well as its filter, so it asks to 'change 2
                    // processors'
                    final Play confirm = dialogWith(screen,
                            TextMatch.exact("Are you sure you want to change 2 processors?"));
                    play.click(confirm.getByRole("button", StroomDom.button("Cancel")));
                    play.click(batch.getByRole("button", StroomDom.button("Cancel")));
                    // Filter Processors opens its expression dialog
                    play.click(play.getByTitle("Filter Processors"));
                    final Play filter = dialog(screen, "Filter Processors");
                    play.click(filter.getByRole("button", StroomDom.button("Cancel")));
                    // The Info popover shows the filter's details.
                    // The info cell is titled 'Info'
                    play.click(play.getAllByTitle("Info").nth(1));
                    screen.findByText("Total Tasks Created");
                    // The processor row is expanded: collapsing it hides the filter
                    play.click(play.querySelectorAll(".expander-cell .expander-cell__icon").nth(0));
                    play.waitFor(() -> play.expect(play.queryByText("Complete")).toBeNull());
                    play.click(play.querySelectorAll(".expander-cell .expander-cell__icon").nth(0));
                    play.findByText("Complete");
                    // Show Tasks for Selected Processor is enabled once a single filter row is selected
                    play.expect(play.getByText("Active Tasks", StroomDom.LINK_TAB_LABEL)).toBeInTheDocument();
                    play.click(play.getByText("Complete"));
                    play.waitFor(() -> play.expect(play.getByTitle("Show Tasks for Selected Processor"))
                            .not().toHaveAttribute("aria-disabled"));
                    expectNoProblems(play);
                })
                // An embedded document that can't be created says why and leaves the property dialog
                // usable: the failure alert had no callback, so OK and Cancel stayed disabled
                .story("FailedEmbeddedCreateKeepsDialogUsable", context ->
                        renderWithPlugins(context, EMBED_FAILS_FIXTURES))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    openStructure(play);
                    play.click(play.findByText("xslt"));
                    play.dblClick(play.findByText("My XSLT"));
                    final Play property = dialog(screen, "Edit Property");
                    play.click(property.querySelector(StroomDom.SELECTION_BOX));
                    play.click(screen.findByText("Embedded"));
                    play.waitFor(() -> play.expect(property.getByDisplayValue("EMBEDDED XSLT (xslt)"))
                            .toBeInTheDocument());
                    play.click(property.getByRole("button", StroomDom.button("OK")));
                    play.click(dialogWith(screen, TextMatch.containing("Unable to create embedded document"))
                            .getByRole("button", StroomDom.button("Close")));
                    play.click(property.getByRole("button", StroomDom.button("Cancel")));
                    play.waitFor(() -> play.expect(screen.queryByText("Edit Property", StroomDom.DIALOG_TITLE))
                            .toBeNull());
                })
                // A batch change that fails says why and leaves the dialog usable: the change had no
                // failure handler, so OK and Cancel stayed disabled after the alert
                .story("FailedBatchChangeKeepsDialogUsable", context -> render(context, BATCH_FAILS_FIXTURES))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.click(play.findByText("Processors", StroomDom.LINK_TAB_LABEL));
                    play.findByText("Complete");
                    play.click(play.getByTitle("Batch Edit Filtered Processors"));
                    final Play batch = dialog(screen, "Batch Change All Filtered Processors");
                    play.click(batch.getByRole("button", StroomDom.button("OK")));
                    play.click(dialogWith(screen, TextMatch.exact("Are you sure you want to change 2 processors?"))
                            .getByRole("button", StroomDom.button("OK")));
                    play.click(dialogWith(screen, TextMatch.containing("Unable to change the processors"))
                            .getByRole("button", StroomDom.button("Close")));
                    play.click(batch.getByRole("button", StroomDom.button("Cancel")));
                    play.waitFor(() -> play.expect(screen.queryByText("Batch Change All Filtered Processors",
                            StroomDom.DIALOG_TITLE)).toBeNull());
                })
                // With data access, the Data tab is there (the default) and Structure is reachable
                .story("WithDataTab", context -> render(context, FIXTURES))
                .withPlay(play -> {
                    // The tabs are link tabs with no 'tab' role
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
                    play.fireEvent().mouseDown(play.querySelector(".drop-down"), EventInit.create());
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
                // Saving with another dirty document (code edited while stepping) opens the picker;
                // OK saves both
                .story("MultiDocumentSave", PipelineEditorStories::renderWithPlugins)
                .withPlay(play -> {
                    final Play screen = play.screen();
                    // The other dirty document can only be the code of an element edited while stepping
                    // (SteppingScreenController.getDirtyDocs), and the pipeline is dirtied by an edit, so the
                    // play makes both dirty first: it removes the xslt element's reference loader, steps
                    // a stream and edits the element's code (the XSLT 'My XSLT', loaded by Stroom's
                    // XsltPlugin)
                    openStructure(play);
                    play.click(play.findByText("xslt"));
                    play.click(play.findByText("Ref Pipeline"));
                    play.click(play.getByTitle("Remove Selected Pipeline Reference"));
                    play.waitFor(() -> play.expect(play.queryByText("Ref Pipeline")).toBeNull());
                    play.click(play.getByTitle("Stepping Mode"));
                    play.click(play.findAllByText("MY_FEED").nth(0));
                    play.findByTitle("Step Forward");
                    play.click(play.getByText("xslt"));
                    final Query code = play.findByText("ORIGINAL XSLT CODE");
                    // Ace's text layer ignores the mouse: click its content to focus the editor
                    play.click(code.closest(".ace_content"));
                    play.keyboard("{Control>}{End}{/Control} EDITED");
                    play.findByText("ORIGINAL XSLT CODE EDITED");
                    // Save with the XSLT dirty: the picker lists both documents
                    play.click(play.getByRole("button", "Save"));
                    final Play picker = dialog(screen, "Save Pipeline: My Pipeline");
                    play.expect(picker.getByText("My Pipeline")).toBeInTheDocument();
                    play.expect(picker.getByText("My XSLT")).toBeInTheDocument();
                    // OK saves both (both ticked by default).
                    // The saves are checked as the PUT requests of the pipeline (PipelinePlugin) and of
                    // the XSLT (XsltPlugin)
                    play.click(picker.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(screen.queryByText("Save Pipeline: My Pipeline",
                            StroomDom.DIALOG_TITLE)).toBeNull());
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.put("/pipeline/v1/" + UUID).toSpyMatcher()));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.put(XSLT_PATH)
                                    .withJsonBodyContaining("{\"data\": \"ORIGINAL XSLT CODE EDITED\"}")
                                    .toSpyMatcher()));
                    expectNoProblems(play);
                })
                // Removing an inherited reference keeps it, struck through, and Remove restores it
                .story("ReferenceRemoveRestore", context -> render(context, REF_FIXTURES))
                .withPlay(play -> {
                    openStructure(play);
                    play.click(play.findByText("xslt"));
                    play.findByText("Inherited Ref");
                    // The 'Inherited From' column names the ancestor the reference comes from.
                    final Query inheritedRow = play.getByText("Inherited Ref").closest(StroomDom.GRID_ROW);
                    play.expect(play.within(inheritedRow).getByText("Parent")).toBeInTheDocument();
                    // Select the row and Remove it: it stays, struck through (a cell class)
                    play.click(play.getByText("REF_FEED"));
                    play.click(play.getByTitle("Remove Selected Pipeline Reference"));
                    play.findByText("Inherited Ref");
                    // GWT puts the class on the cell's text, not on the cell
                    play.waitFor(() -> play.expect(play.getByText("REF_FEED"))
                            .toHaveClass("pipeline-property--removed"));
                    // Remove again restores it, which the button's title then says
                    play.click(play.getByText("REF_FEED"));
                    play.waitFor(() -> play.expect(play.queryByTitle("Remove Selected Pipeline Reference"))
                            .toBeNull());
                    play.click(play.getByTitle("Restore Selected Pipeline Reference"));
                    play.waitFor(() -> play.expect(play.getByText("REF_FEED"))
                            .toHaveClass("pipeline-property--inherited"));
                    expectNoProblems(play);
                })
                // Edit Pipeline Reference shows the reference and Cancel keeps it; New with no
                // pipeline chosen is refused, saying so
                .story("EditPipelineReference", context -> render(context, FIXTURES))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    openStructure(play);
                    play.click(play.findByText("xslt"));
                    play.click(play.findByText("Ref Pipeline"));
                    play.click(play.getByTitle("Edit Selected Pipeline Reference"));
                    final Play edit = dialog(screen, "Edit Pipeline Reference");
                    play.expect(edit.getByText("xslt")).toBeInTheDocument();
                    play.waitFor(() -> play.expect(edit.getByText("Ref Pipeline")).toBeInTheDocument());
                    play.waitFor(() -> play.expect(edit.getByText("MY_FEED")).toBeInTheDocument());
                    play.click(edit.getByRole("button", StroomDom.button("Cancel")));
                    play.waitFor(() -> play.expect(screen.queryByText("Edit Pipeline Reference",
                            StroomDom.DIALOG_TITLE)).toBeNull());
                    play.expect(play.getByText("Ref Pipeline")).toBeInTheDocument();
                    play.click(play.getByTitle("New Pipeline Reference"));
                    final Play add = dialog(screen, "New Pipeline Reference");
                    play.click(add.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.ALERT_SPY))
                            .toHaveBeenCalledWith(ValueMatcher.stringContaining("You must specify a pipeline")));
                    play.expect(screen.getByText("New Pipeline Reference", StroomDom.DIALOG_TITLE))
                            .toBeInTheDocument();
                    play.expect(play.spy(ScreenHarness.UNHANDLED_REQUEST_SPY)).not().toHaveBeenCalled();
                })
                // Edit Element shows the element's own description: one typed and cancelled isn't
                // shown again (the dialog kept it, and OK then gave it to whichever element was
                // edited), and one saved is
                .story("EditElementDescription", context -> render(context, FIXTURES))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    openStructure(play);
                    play.click(play.findByText("xslt"));
                    play.click(play.getByTitle("Edit Pipeline Element"));
                    final Play edit = dialog(screen, "Edit Element");
                    final Query description = edit.getByLabelText("Description (optional)");
                    play.expect(description).toHaveValue("");
                    play.type(description, "Typed then cancelled");
                    play.click(edit.getByRole("button", StroomDom.button("Cancel")));
                    play.waitFor(() -> play.expect(screen.queryByText("Edit Element", StroomDom.DIALOG_TITLE))
                            .toBeNull());
                    play.click(play.getByTitle("Edit Pipeline Element"));
                    final Play editAgain = dialog(screen, "Edit Element");
                    final Query again = editAgain.getByLabelText("Description (optional)");
                    play.expect(again).toHaveValue("");
                    play.type(again, "Turns XML into events");
                    play.click(editAgain.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.spy(ON_DIRTY)).toHaveBeenCalledWith(true));
                    play.click(play.getByTitle("Edit Pipeline Element"));
                    play.expect(dialog(screen, "Edit Element").getByLabelText("Description (optional)"))
                            .toHaveValue("Turns XML into events");
                    expectNoProblems(play);
                })
                // A structure edit dirties the document
                .story("StructureEditDirtiesTheDocument", context -> render(context, FIXTURES))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    openStructure(play);
                    play.findByText("xslt");
                    // The dirty state is a spy on the editor's dirty listener
                    play.expect(play.spy(ON_DIRTY)).not().toHaveBeenCalledWith(true);
                    play.click(play.getByText("xslt"));
                    play.click(play.getByTitle("Remove Pipeline Element"));
                    play.click(dialogWith(screen, TextMatch.exact("Are you sure you want to remove this element?"))
                            .getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.queryByText("xslt")).toBeNull());
                    play.waitFor(() -> play.expect(play.spy(ON_DIRTY)).toHaveBeenCalledWith(true));
                    expectNoProblems(play);
                })
                // Read only, Ctrl+X detaches nothing, as nothing can be moved
                .story("KeyboardMoveReadOnly", context -> render(context, COMPLEX_FIXTURES, true))
                .withPlay(play -> {
                    openStructure(play);
                    play.findByText("rawWriter");
                    final Play tree = play.within(play.querySelector(".pipeline-tree__items"));
                    play.click(elementBox(tree, "rawWriter"));
                    play.waitFor(() -> play.expect(elementBox(tree, "rawWriter")).toHaveFocus());
                    play.keyboard("{Control>}x{/Control}");
                    play.expect(play.querySelectorAll("." + DETACHED)).toHaveLength(0);
                    play.expect(play.spy(ON_DIRTY)).not().toHaveBeenCalledWith(true);
                    expectNoProblems(play);
                })
                // The Structure tab, nothing selected, as an editable partner for StructureReadOnly
                .story("StructureEditable", context -> render(context, FIXTURES, false))
                .withPlay(play -> {
                    waitForStructure(play);
                    expectNoProblems(play);
                })
                // The Structure tab, nothing selected, when the user may only view the pipeline
                .story("StructureReadOnly", context -> render(context, FIXTURES, true))
                .withPlay(play -> {
                    waitForStructure(play);
                    play.expect(play.getByText("Read only", ".docTab-readOnlyNote")).toBeVisible();
                    expectNoProblems(play);
                })
                // The xslt element's properties and reference loaders, as an editable partner for
                // PropertiesReadOnly
                .story("PropertiesEditable", context -> render(context, FIXTURES, false))
                .withPlay(play -> {
                    selectXslt(play);
                    expectNoProblems(play);
                })
                // The xslt element's properties and reference loaders, when the user may only view the
                // pipeline
                .story("PropertiesReadOnly", context -> render(context, FIXTURES, true))
                .withPlay(play -> {
                    selectXslt(play);
                    play.expect(play.getByText("Read only", ".docTab-readOnlyNote")).toBeVisible();
                    expectNoProblems(play);
                });
    }

    // Opens the Structure tab and waits for its elements
    private static void waitForStructure(final Play play) {
        openStructure(play);
        play.findByText("Source");
        play.findByText("parser");
        play.findByText("xslt");
    }

    // Opens the Structure tab and selects the xslt element, showing its properties and references
    private static void selectXslt(final Play play) {
        waitForStructure(play);
        play.click(play.getByText("xslt"));
        play.waitFor(() -> play.expect(play.getByText("The XSLT to use")).toBeInTheDocument());
        play.waitFor(() -> play.expect(play.getByText("Ref Pipeline")).toBeInTheDocument());
    }

    // Stroom's Data tab is the default (for a user who may view data), so the Structure link tab is
    // chosen first
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

    // The box of an element of the tree
    private static Query elementBox(final Play tree, final String name) {
        return tree.getByText(name, ".pipeline-element__label").closest(".pipeline-element");
    }

    // Presses and releases the right mouse button on an element, as a right-click does
    private static void rightClick(final Play play, final Query element) {
        play.fireEvent().mouseDown(element, EventInit.create().atCentreOf(element).button(2).buttons(2));
        play.fireEvent().mouseUp(element, EventInit.create().atCentreOf(element).button(2));
    }

    // How far an element is scrolled across, and how far it can be
    private static double scrollLeft(final Query element) {
        return element.element().get().getScrollLeft();
    }

    private static double scrollLeftMax(final Query element) {
        return element.element().get().getScrollWidth() - element.element().get().getClientWidth();
    }

    // Presses on rawWriter and moves past the drag threshold, lifting it (and rawAppender) out of the
    // tree
    private static void liftRawWriter(final Play play, final Play tree) {
        final Query rawWriter = elementBox(tree, "rawWriter");
        play.fireEvent().mouseDown(rawWriter, EventInit.create().atCentreOf(rawWriter).buttons(1));
        play.fireEvent().mouseMove(rawWriter, EventInit.create().relativeTo(rawWriter, 60, 40).buttons(1));
        play.waitFor(() -> play.expect(tree.queryByText("rawWriter")).toBeNull());
    }

    // rawWriter (and rawAppender) are back where they were: the parser's first child, with nothing
    // marked
    private static void expectRawWriterBack(final Play play, final Play tree) {
        play.waitFor(() -> play.expect(tree.queryByText("rawWriter")).toBeInTheDocument());
        play.waitFor(() -> play.expect(elementBox(tree, "rawWriter")).toHaveAttribute("aria-level", "3"));
        play.expect(elementBox(tree, "rawWriter")).toHaveAttribute("aria-posinset", "1");
        play.expect(elementBox(tree, "rawAppender")).toHaveAttribute("aria-level", "4");
        play.expect(play.querySelectorAll("." + HOTSPOT + ", ." + REFUSED)).toHaveLength(0);
    }

    // A box's type icon, and the "not allowed" icon that takes its place while it refuses what is
    // moved
    private static Query typeIcon(final Play tree, final String name) {
        return tree.within(elementBox(tree, name)).querySelector(".pipeline-element__type-icon");
    }

    private static Query refusedIcon(final Play tree, final String name) {
        return tree.within(elementBox(tree, name)).querySelector(".pipeline-element__refused-icon");
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
                        // The stream types a reference can load from
                        .get("/meta/v1/getTypes", RestReply.json("[\"Reference\", \"Context\"]"))
                        .post("/processorFilter/v1/find", RestReply.json(processorRows))
                        .post("/expression/v1/validate", RestReply.json("{\"ok\": true}"))
                        .addAll(PipelineFixtures.stepping("xslt"))
                        .route(RequestMatcher.post("/processorTask/v1/*"), RestReply.json(NO_ROWS))
                        .build())
                .build();
    }

    // As the app opens a pipeline with its document plugins registered: the explorer's
    // OpenDocumentEvent goes to PipelinePlugin (through the DocumentPluginEventManager), which opens
    // the tab, and saves it (the tab's Save) with its 'Save Pipeline' picker; a stepping element's
    // code is loaded and saved by XsltPlugin
    private static Widget renderWithPlugins(final StoryContext context) {
        return renderWithPlugins(context, MULTI_SAVE_FIXTURES);
    }

    // As above, with the given fixtures
    private static Widget renderWithPlugins(final StoryContext context, final RestFixtures fixtures) {
        final ProcessingScreenGinjector injector = GWT.create(ProcessingScreenGinjector.class);
        final ScreenHarness harness = ScreenHarness.builder(context, fixtures)
                .injector(injector)
                .realAlerts()
                .build();
        harness.getSecurityContext().setDocumentPermission(DocumentPermission.EDIT);
        StoryDocumentPlugins.register(harness, injector.getDocumentPluginEventManager(),
                        injector.getPipelinePlugin(), injector.getXsltPlugin())
                .showOpenedTabs();
        harness.afterStartUp(() -> OpenDocumentEvent.fire(harness.getHasHandlers(), DOC_REF, true));
        return harness.asWidget();
    }

    private static Widget render(final StoryContext context, final RestFixtures fixtures) {
        return render(context, fixtures, false);
    }

    private static Widget render(final StoryContext context, final RestFixtures fixtures, final boolean readOnly) {
        final ProcessingScreenGinjector injector = GWT.create(ProcessingScreenGinjector.class);
        final ScreenHarness harness = ScreenHarness.builder(context, fixtures)
                .injector(injector)
                .realAlerts()
                .build();
        harness.getSecurityContext().setDocumentPermission(readOnly
                ? DocumentPermission.VIEW
                : DocumentPermission.EDIT);
        final Spy onDirty = harness.fn(ON_DIRTY);
        harness.fn(ON_EMBED);
        // Opened once Stroom has started, as PipelinePlugin opens a document: load it, check the
        // user may edit it, read it into the editor and show the tab
        harness.afterStartUp(() -> {
            final DocumentEditorHost<PipelineDoc> presenter = new DocumentEditorHost<>(
                    harness.getEventBus(), injector.getPipelineEditor(), PipelineDoc.TYPE);
            final PipelineResource resource = GWT.create(PipelineResource.class);
            harness.getRestFactory()
                    .create(resource)
                    .method(res -> res.fetch(UUID))
                    .onSuccess(doc -> {
                        harness.addRegistration(presenter.addDirtyListener(onDirty::call));
                        presenter.setReadOnly(readOnly);
                        presenter.read(DOC_REF, doc);
                        harness.show(presenter);
                    })
                    .taskMonitorFactory(presenter)
                    .exec();
        });
        return harness.asWidget();
    }
}
