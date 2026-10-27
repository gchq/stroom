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

package stroom.mcp.impl.tools;

import stroom.ai.impl.tools.KeptMapping;
import stroom.ai.impl.tools.ToolResult;
import stroom.docref.DocRef;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.TextConverterDoc;
import stroom.pipeline.shared.XsltDoc;
import stroom.pipeline.shared.data.PipelineDataBuilder;
import stroom.pipeline.shared.data.PipelineLayer;

import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

/// The pipeline write tools' world: the in-memory explorer and feeds of [FeedWorld], pipelines whose layers Stroom
/// builds from the pipeline documents held there (each with its templates'), the property types Stroom declares,
/// and the tools on it.
final class PipelineWorld {

    static final String BUILD = "acme-v1";
    static final String TEMPLATE_UUID = "d7c55950-7427-48ab-a1b6-11bc7b449dfd";
    static final String LOADER = "8b5c6a4e-1f0d-4a3e-9c2b-7d6e5f4a3b2c";
    /// The property types the tests' elements have, as Stroom declares them.
    static final Map<String, Map<String, String>> DECLARED = declared();

    final FeedWorld feeds = new FeedWorld();
    final BuildWorld world = feeds.world;
    final PipelinePropertyTypes propertyTypes = Mockito.mock(PipelinePropertyTypes.class);
    final ReferenceData referenceData = Mockito.mock(ReferenceData.class);
    final PipelineTemplates templates = Mockito.mock(PipelineTemplates.class);
    final PipelineDocs pipelineDocs = new PipelineDocs(world.documentAccess);
    final BuildIndexes buildIndexes = new BuildIndexes(world.guard(), world.pipelineShapes, world.documentAccess,
            pipelineDocs, new PipelineIndex(() -> feeds.docFinder, world.documentAccess), feeds.plan);
    final PipelineWrites writes = new PipelineWrites(world.pipelineShapes, pipelineDocs, propertyTypes,
            world.documentAccess, () -> feeds.docFinder, feeds.feedNames, referenceData, world.guard(), feeds.plan,
            buildIndexes);

    PipelineWorld() {
        Mockito.when(propertyTypes.declared()).thenReturn(DECLARED);
        Mockito.when(world.pipelineService.fetchPipelineLayers(any())).thenAnswer(inv ->
                layersOf(((DocRef) inv.getArgument(0)).getUuid()));
        Mockito.when(feeds.docFinder.findByName(eq(PipelineDoc.TYPE), anyString(), anyBoolean())).thenAnswer(inv ->
                named(PipelineDoc.TYPE, inv.getArgument(1)));
        Mockito.when(feeds.docFinder.findByName(eq(PipelineDoc.TYPE), anyString())).thenAnswer(inv ->
                named(PipelineDoc.TYPE, inv.getArgument(1)));
    }

    /// @return create_pipeline, on this world.
    CreatePipelineTool createTool() {
        return new CreatePipelineTool(writes, pipelineDocs, world.pipelineShapes, world.guard(), feeds.consent,
                feeds.plan, world.records(), templates);
    }

    /// @return copy_pipeline, on this world.
    CopyPipelineTool copyTool() {
        return new CopyPipelineTool(writes, pipelineDocs, world.pipelineShapes, templates, world.documentAccess,
                world.guard(), world.records(), feeds.consent, feeds.plan);
    }

    /// @return update_pipeline, on this world.
    UpdatePipelineTool updateTool() {
        return new UpdatePipelineTool(writes, feeds.plan);
    }

    /// Adds a template, outside the workspace.
    PipelineDoc template(final String uuid, final String name, final PipelineLayers layer) {
        world.add(PipelineDoc.TYPE, name, world.folder("System/Template Pipelines").uuid, uuid);
        return pipeline(uuid, name, null, layer);
    }

    /// Adds a pipeline of a build, the tools' own.
    PipelineDoc buildPipeline(final String uuid, final String name, final DocRef parent, final PipelineLayers layer) {
        world.inBuild(BUILD, PipelineDoc.TYPE, name, uuid);
        return pipeline(uuid, name, parent, layer);
    }

    /// Adds a pipeline outside the workspace, as people make them.
    PipelineDoc productionPipeline(final String uuid, final String name, final DocRef parent,
                                   final PipelineLayers layer) {
        world.add(PipelineDoc.TYPE, name, world.folder("System/Feeds/Acme").uuid, uuid);
        return pipeline(uuid, name, parent, layer);
    }

    /// Adds an XSLT to the build, its description keeping a translation mapping when `mapping` is given.
    DocRef xslt(final String uuid, final String name, final Map<String, Object> mapping) {
        final DocRef ref = world.inBuild(BUILD, XsltDoc.TYPE, name, uuid);
        world.docs.put(uuid, XsltDoc.builder().uuid(uuid).name(name).version("v0").data("<xsl/>")
                .description(mapping == null
                        ? ""
                        : KeptMapping.withMapping("", "translation", Map.of("mapping", mapping)))
                .build());
        return ref;
    }

    /// Adds a text converter to the build.
    DocRef textConverter(final String uuid, final String name) {
        final DocRef ref = world.inBuild(BUILD, TextConverterDoc.TYPE, name, uuid);
        world.docs.put(uuid, TextConverterDoc.builder().uuid(uuid).name(name).version("v0").data("<dataSplitter/>")
                .build());
        return ref;
    }

    /// @return The pipeline's document as it is now.
    PipelineDoc pipelineDoc(final String uuid) {
        return (PipelineDoc) world.docs.get(uuid);
    }

    /// @return The build's pipeline of the name, as it is now.
    PipelineDoc pipelineNamed(final String name) {
        return world.docs.values().stream()
                .filter(doc -> doc instanceof final PipelineDoc pipeline && name.equals(pipeline.getName()))
                .map(PipelineDoc.class::cast)
                .findFirst()
                .orElse(null);
    }

    /// @return The tool's result, read.
    static Map<String, Object> json(final ToolResult result) {
        if (result.isError()) {
            throw new AssertionError("The tool refused: " + result.text());
        }
        return PortedToolCalls.json(result.text());
    }

    private PipelineDoc pipeline(final String uuid, final String name, final DocRef parent,
                                 final PipelineLayers layer) {
        final PipelineDoc doc = PipelineDoc.builder().uuid(uuid).name(name).version("v0").parentPipeline(parent)
                .pipelineData(layer == null
                        ? null
                        : layer.data())
                .build();
        world.docs.put(uuid, doc);
        return doc;
    }

    // A pipeline's layers as Stroom builds them, root template first.
    private List<PipelineLayer> layersOf(final String uuid) {
        final List<PipelineLayer> layers = new ArrayList<>();
        PipelineDoc doc = pipelineDoc(uuid);
        if (doc == null) {
            throw new RuntimeException("Document not found: " + uuid);
        }
        while (doc != null) {
            layers.addFirst(new PipelineLayer(doc.asDocRef(), Objects.requireNonNullElseGet(doc.getPipelineData(),
                    () -> new PipelineDataBuilder().build())));
            doc = doc.getParentPipeline() == null
                    ? null
                    : pipelineDoc(doc.getParentPipeline().getUuid());
        }
        return layers;
    }

    private List<DocRef> named(final String type, final String name) {
        return world.nodes.values().stream()
                .filter(node -> type.equals(node.type) && node.name.equals(name))
                .map(BuildWorld.Node::docRef)
                .toList();
    }

    private static Map<String, Map<String, String>> declared() {
        final Map<String, Map<String, String>> declared = new LinkedHashMap<>();
        declared.put("JSONParser", Map.of("addRootObject", "boolean"));
        declared.put("XMLParser", Map.of());
        declared.put("DSParser", Map.of("textConverter", "TextConverter"));
        declared.put("XMLFragmentParser", Map.of("textConverter", "TextConverter"));
        declared.put("CombinedParser", Map.of("textConverter", "TextConverter", "type", "String"));
        declared.put("SplitFilter", Map.of("splitCount", "int", "splitDepth", "int"));
        declared.put("RecordCountFilter", Map.of("countRead", "boolean"));
        declared.put("RecordOutputFilter", Map.of());
        declared.put("StreamAppender", Map.of("streamType", "String"));
        declared.put("XSLTFilter", Map.of("xslt", "XSLT"));
        declared.put("SchemaFilter", Map.of("schemaGroup", "String", "namespaceURI", "String",
                "schemaValidation", "boolean"));
        declared.put("StandardKafkaProducer", Map.of("kafkaConfig", "KafkaConfig"));
        declared.put("ElasticIndexingFilter", Map.of("indexName", "String", "cluster", "ElasticCluster"));
        declared.put("IndexingFilter", Map.of("index", "Index"));
        return declared;
    }
}
