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
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.PipelineResource;
import stroom.pipeline.shared.data.PipelineElement;
import stroom.pipeline.shared.data.PipelineLayer;
import stroom.pipeline.shared.data.PipelineLink;
import stroom.pipeline.structure.client.DefaultPipelineTreeBuilder;
import stroom.pipeline.structure.client.PipelineElementTypes;
import stroom.pipeline.structure.client.PipelineModel;
import stroom.pipeline.structure.client.component.PipelineTree;
import stroom.task.client.DefaultTaskMonitorFactory;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/// Stories of `App/Main/PipelineTree`, showing Stroom's real [PipelineTree] (the pipeline
/// structure's element tree) with the [PipelineModel] Stroom builds for an inheritance stack
/// (`PipelineModelFactory`): a parent layer adding `parser` under `Source`, and the edited child
/// layer adding `xslt` under `parser`. Each has an embedded document: `parser`'s from the parent,
/// `xslt`'s its own.
///
/// | Stroom endpoint | Used for |
/// |---|---|
/// | `GET /pipeline/v1/propertyTypes` (`PipelineElementTypesFactory`) | the element types |
/// | `POST /pipeline/v1/fetchPipelineLayers`, then `PipelineModel.build()` | the layers |
///
/// The model checks are made on Stroom's `PipelineModel` (`getChildMap`, `diff`, `addElement`,
/// `removeElement`), each on a model built afresh from the fetched layers.
public final class PipelineTreeStories {

    /// The name of the spy recording the element selected.
    static final String ON_SELECT = "onSelect";

    private static final String LAYERS = """
            [
              {"sourcePipeline": {"type": "Pipeline", "uuid": "parent", "name": "Parent"},
                "pipelineData": {"elements": {"add": [{"id": "parser", "type": "CombinedParser"}]},
                  "properties": {"add": [{"element": "parser", "name": "textConverter",
                    "value": {"entity": {"type": "TextConverter", "uuid": "tc", "name": "EMBEDDED TC"},
                      "embedded": true}}]},
                  "links": {"add": [{"from": "Source", "to": "parser"}]}}},
              {"sourcePipeline": {"type": "Pipeline", "uuid": "child", "name": "Child"},
                "pipelineData": {"elements": {"add": [{"id": "xslt", "type": "XSLTFilter"}]},
                  "properties": {"add": [{"element": "xslt", "name": "xslt",
                    "value": {"entity": {"type": "XSLT", "uuid": "x", "name": "EMBEDDED XSLT"},
                      "embedded": true}}]},
                  "links": {"add": [{"from": "parser", "to": "xslt"}]}}}
            ]""";

    private static final RestFixtures FIXTURES = RestFixtures.builder()
            .get(PipelineFixtures.PROPERTY_TYPES_PATH, RestReply.json(PipelineFixtures.PROPERTY_TYPES))
            .post("/pipeline/v1/fetchPipelineLayers", RestReply.json(LAYERS))
            .build();

    // The element types, layers and model of the latest rendering, for the play's model checks
    private static PipelineElementTypes elementTypes;
    private static List<PipelineLayer> layers;
    private static PipelineModel renderedModel;

    private PipelineTreeStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Main/PipelineTree", PipelineTreeStories.class)
                .story("Structure", PipelineTreeStories::render)
                .withPlay(play -> {
                    // The merged structure renders Source -> parser -> xslt
                    play.findByText("Source");
                    play.findByText("parser");
                    // Each element's name says what its icons show, which are hover tips only
                    final Query xslt = play.getByText("xslt", ".pipeline-element__label").closest(".pipeline-element");
                    play.expect(xslt).toHaveAttribute("aria-label", "xslt, XSLT Filter, has embedded document");
                    final Query parser = play.getByText("parser", ".pipeline-element__label")
                            .closest(".pipeline-element");
                    play.expect(parser).toHaveAttribute("aria-label",
                            "parser, Combined Parser, has embedded document inherited from parent pipeline");
                    final Query embedded = play.within(xslt).querySelector(".pipeline-element__embedded");
                    play.expect(embedded).toHaveAttribute("aria-hidden", "true");
                    play.expect(embedded).toHaveAttribute("title", "Has embedded document");
                    play.expect(play.within(xslt).querySelector(".pipeline-element__type-icon"))
                            .toHaveAttribute("aria-hidden", "true");
                    play.expect(play.querySelectorAll(".pipeline-tree__items [role='img']")).toHaveLength(0);
                    // Selecting a node reports it
                    play.click(play.findByText("xslt"));
                    play.waitFor(() -> play.expect(play.spy(ON_SELECT)).toHaveBeenCalled());
                    final Spy onSelect = play.spy(ON_SELECT);
                    play.expect("the element selected", () -> onSelect.getLastCall().get(0)).toBe("xslt");

                    // The model: the merge's elements and links
                    play.expect("the elements", () -> elementIds(renderedModel))
                            .toEqual(List.of("Source", "parser", "xslt"));
                    play.expect("Source's children", () -> childIds(renderedModel, "Source"))
                            .toEqual(List.of("parser"));
                    play.expect("parser's children", () -> childIds(renderedModel, "parser")).toEqual(List.of("xslt"));
                    // The child layer's diff adds xslt and its link (parser is inherited)
                    play.expect("the diff's added elements", () -> diffAddedElements(newModel()))
                            .toEqual(List.of("xslt"));
                    play.expect("the diff's removed elements",
                            () -> newModel().diff().getRemovedElements().size()).toBe(0);
                    play.expect("the diff's added links", () -> diffAddedLinks(newModel()))
                            .toContain("parser -> xslt");
                    // Adding a Stream Appender under xslt
                    play.expect("xslt's children after adding 'out'", () -> childIds(withOut(), "xslt"))
                            .toEqual(List.of("out"));
                    // Removing xslt hides it
                    play.expect("the elements after removing xslt", () -> elementIds(withoutXslt()))
                            .toEqual(List.of("Source", "parser"));
                    // Property and reference edits are made by the property and reference list
                    // controllers, not PipelineModel (see PipelineEditor's stories), and Stroom always
                    // adds Source (fixSourceNodes), so this story makes no model checks of those
                    play.expect(play.spy(ScreenHarness.ALERT_SPY)).not().toHaveBeenCalled();
                    play.expect(play.spy(ScreenHarness.UNHANDLED_REQUEST_SPY)).not().toHaveBeenCalled();
                });
    }

    // A model built afresh from the fetched layers, as PipelineModelFactory builds it
    private static PipelineModel newModel() {
        final PipelineModel model = new PipelineModel(elementTypes);
        model.setPipelineLayer(layers.get(layers.size() - 1));
        model.setBaseStack(new ArrayList<>(layers.subList(0, layers.size() - 1)));
        model.build();
        return model;
    }

    private static PipelineModel withOut() {
        final PipelineModel model = newModel();
        model.addElement(element(model, "xslt"),
                elementTypes.getElementType(new PipelineElement("out", "StreamAppender")),
                "out", null, null);
        return model;
    }

    private static PipelineModel withoutXslt() {
        final PipelineModel model = newModel();
        model.removeElement(element(model, "xslt"));
        return model;
    }

    private static PipelineElement element(final PipelineModel model, final String id) {
        for (final PipelineElement element : model.getPipelineElements(e -> true)) {
            if (id.equals(element.getId())) {
                return element;
            }
        }
        return null;
    }

    private static List<String> elementIds(final PipelineModel model) {
        final List<String> ids = new ArrayList<>();
        for (final PipelineElement element : model.getPipelineElements(e -> true)) {
            ids.add(element.getId());
        }
        ids.sort(String::compareTo);
        return ids;
    }

    private static List<String> childIds(final PipelineModel model, final String id) {
        final List<String> ids = new ArrayList<>();
        for (final Map.Entry<PipelineElement, List<PipelineElement>> entry : model.getChildMap().entrySet()) {
            if (id.equals(entry.getKey().getId())) {
                for (final PipelineElement child : entry.getValue()) {
                    ids.add(child.getId());
                }
            }
        }
        return ids;
    }

    private static List<String> diffAddedElements(final PipelineModel model) {
        final List<String> ids = new ArrayList<>();
        for (final PipelineElement element : model.diff().getAddedElements()) {
            ids.add(element.getId());
        }
        return ids;
    }

    private static List<String> diffAddedLinks(final PipelineModel model) {
        final List<String> links = new ArrayList<>();
        for (final PipelineLink link : model.diff().getAddedLinks()) {
            links.add(link.getFrom() + " -> " + link.getTo());
        }
        return links;
    }

    private static Widget render(final StoryContext context) {
        final ProcessingScreenGinjector injector = GWT.create(ProcessingScreenGinjector.class);
        final ScreenHarness harness = ScreenHarness.builder(context, FIXTURES)
                .injector(injector)
                .build();
        final Spy onSelect = harness.fn(ON_SELECT);
        final PipelineTree tree = injector.getPipelineTree();
        tree.setPipelineTreeBuilder(new DefaultPipelineTreeBuilder());
        harness.addRegistration(tree.getSelectionModel().addSelectionChangeHandler(event -> {
            final PipelineElement selected = tree.getSelectionModel().getSelectedObject();
            onSelect.call(selected == null
                    ? null
                    : selected.getId());
        }));
        final DocRef pipeline = DocRef.builder().type(PipelineDoc.TYPE).uuid("child").name("Child").build();
        final PipelineResource pipelineResource = GWT.create(PipelineResource.class);
        // As PipelineModelFactory builds the model, keeping the layers for the play's checks
        // The tree is a component, with no task monitor of its own, so the story's is used
        final DefaultTaskMonitorFactory taskMonitorFactory = new DefaultTaskMonitorFactory(harness.getHasHandlers());
        harness.afterStartUp(() -> injector.getPipelineElementTypesFactory().get(taskMonitorFactory, types ->
                harness.getRestFactory()
                        .create(pipelineResource)
                        .method(res -> res.fetchPipelineLayers(pipeline))
                        .onSuccess(result -> {
                            elementTypes = types;
                            layers = result;
                            renderedModel = newModel();
                            tree.setModel(renderedModel);
                        })
                        .taskMonitorFactory(taskMonitorFactory)
                        .exec()));
        harness.showWidget(tree);
        return harness.asWidget();
    }
}
