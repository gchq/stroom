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

import stroom.ai.impl.tools.AbstractBuiltInTool;
import stroom.ai.impl.tools.DocumentAccess;
import stroom.ai.impl.tools.KeptMapping;
import stroom.ai.impl.tools.PyText;
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolDefinition;
import stroom.ai.impl.tools.ToolPermissions;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.ToolResult;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.impl.tools.indexing.FieldPlan;
import stroom.ai.impl.tools.indexing.IndexingHelpers;
import stroom.ai.impl.tools.indexing.PlannedField;
import stroom.ai.shared.AiToolCategory;
import stroom.docref.DocRef;
import stroom.index.shared.LuceneIndexDoc;
import stroom.mcp.impl.tools.CreatePipelineTool.FromTemplate;
import stroom.mcp.impl.tools.PipelineShapes.Shape;
import stroom.mcp.impl.tools.WriteGuard.BuildDoc;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.XsltDoc;
import stroom.search.elastic.shared.ElasticClusterDoc;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// `create_indexing_pipeline` - stage 2: creates an indexing pipeline as a child of an indexing template, setting
/// its XSLT and where it indexes (the Stroom MCP server's `tools/indexing.py` `create_indexing_pipeline`, with
/// `_events_available` and `_missing_index_fields`). The pipeline is made by create_pipeline's path, so the user
/// confirms the template and name first; a Lucene index lacking fields the XSLT's plan writes gets them from the
/// plan.
class CreateIndexingPipelineTool extends AbstractBuiltInTool {

    static final String NAME = "create_indexing_pipeline";
    static final String BUILD_ARG = "build";
    static final String NAME_ARG = "name";
    static final String TEMPLATE_UUID_ARG = "template_uuid";
    static final String XSLT_UUID_ARG = "xslt_uuid";
    static final String INDEX_UUID_ARG = "index_uuid";
    static final String INDEX_NAME_ARG = "index_name";
    static final String CLUSTER_UUID_ARG = "cluster_uuid";
    static final String EVENTS_STREAM_IDS_ARG = "events_stream_ids";

    // The records:2 data elements an XSLT written by hand writes.
    private static final Pattern DATA_NAME = Pattern.compile("<data\\s+name=\"([^\"{]+)\"");
    // How many missing fields a refusal names.
    private static final int MISSING_NAMED = 10;

    private final CreatePipelineTool pipelines;
    private final PipelineShapes pipelineShapes;
    private final IndexingInputs inputs;
    private final IndexFields indexFields;
    private final DocumentAccess documentAccess;
    private final WriteGuard guard;
    private final BuildPlan plan;

    @Inject
    CreateIndexingPipelineTool(final CreatePipelineTool pipelines,
                               final PipelineShapes pipelineShapes,
                               final IndexingInputs inputs,
                               final IndexFields indexFields,
                               final DocumentAccess documentAccess,
                               final WriteGuard guard,
                               final BuildPlan plan) {
        this.pipelines = pipelines;
        this.pipelineShapes = pipelineShapes;
        this.inputs = inputs;
        this.indexFields = indexFields;
        this.documentAccess = documentAccess;
        this.guard = guard;
        this.plan = plan;
    }

    @Override
    public AiToolCategory category() {
        return AiToolCategory.INDEXING;
    }

    @Override
    public boolean isReadOnly() {
        // It creates a pipeline, and may add fields to its Lucene index.
        return false;
    }

    @Override
    public boolean confirmsItself() {
        // The user confirms the pipeline (create_pipeline's form), but not the index fields added after it, as the
        // MCP server's tool didn't.
        return false;
    }

    @Override
    public boolean changesOnlyItsBuild() {
        // Through WriteGuard, only what is in the build's folder: the pipeline, and the fields of the build's own
        // index (IndexFields refuses any other).
        return true;
    }

    @Override
    public ToolPermissions permissions() {
        return ToolPermissions.builder()
                .checksDocuments(DocumentPermission.VIEW, DocumentPermission.EDIT)
                .note("The pipeline is created in the build folder as the user, who needs Create there, Edit on "
                      + "the pipeline, and View (or Use) on the template, the XSLT and the index doc. A Lucene "
                      + "index's missing fields are added through Stroom's index service, the user needing Edit on "
                      + "the build's index doc.")
                .build();
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(NAME)
                .description(BuildPlan.onboardingLabel(NAME)
                             + "Stage 2: create an indexing pipeline as a child of an indexing template, setting "
                             + "its XSLT and where it indexes: the Lucene Index doc, or the Elasticsearch index "
                             + "name (and cluster if the template leaves it open). It reads Events streams, so it "
                             + "comes after the events pipeline (stage 1) has produced them, or takes an existing "
                             + "Events feed's streams as events_stream_ids.")
                .parameters(ObjectSchema.builder()
                        .addStringProperty(BUILD_ARG, CreateFeedTool.BUILD_DESCRIPTION)
                        .addStringProperty(NAME_ARG, "Pipeline name, e.g. 'Acme - Indexing'.")
                        .addStringProperty(TEMPLATE_UUID_ARG, "Indexing template (find_pipeline_templates "
                                                              + "stage=indexing).")
                        .addStringProperty(XSLT_UUID_ARG, "The indexing XSLT, e.g. created from "
                                                          + "draft_index_mapping's draft.")
                        .addStringProperty(INDEX_UUID_ARG, "Lucene: the Index doc. Elasticsearch: the Elastic "
                                                           + "Index doc, which names the index and its cluster (set "
                                                           + "on the pipeline, over the template's own cluster).")
                        .addStringProperty(INDEX_NAME_ARG, "Elasticsearch: the index or data stream name.")
                        .addStringProperty(CLUSTER_UUID_ARG, "Elasticsearch: the cluster, if the template does "
                                                             + "not already set one.")
                        .addProperty(EVENTS_STREAM_IDS_ARG, stringArraySchema("Events streams this pipeline will "
                                                                              + "index (stage 1's output, or an "
                                                                              + "existing Events feed's). Needed "
                                                                              + "when the build has no events "
                                                                              + "pipeline of its own."))
                        .required(BUILD_ARG, NAME_ARG, TEMPLATE_UUID_ARG, XSLT_UUID_ARG)
                        .build())
                .build();
    }

    @Override
    public ToolResult execute(final ToolCall call, final ToolCallContext context) {
        try {
            return JsonResults.of(create(call, context));
        } catch (final ToolRefusal e) {
            return JsonResults.refused(e);
        } catch (final PipelineUnreadableException e) {
            return JsonResults.refused("Stroom can't load the pipeline: " + e.getMessage());
        }
    }

    private Map<String, Object> create(final ToolCall call, final ToolCallContext context) {
        final String build = required(call, BUILD_ARG);
        final String name = required(call, NAME_ARG);
        final String templateUuid = required(call, TEMPLATE_UUID_ARG);
        final String xsltUuid = required(call, XSLT_UUID_ARG);
        final String indexUuid = stringArg(call, INDEX_UUID_ARG);
        final List<Long> eventsStreamIds = StartOnboardingTool.streamIds(call, EVENTS_STREAM_IDS_ARG);
        plan.rememberBuild(context, build);
        final Shape shape = pipelineShapes.shape(templateUuid);
        if (!"indexing".equals(shape.stage()) && !"discovery".equals(shape.stage())) {
            throw new ToolRefusal("That template is a " + shape.stage() + " template, not an indexing one");
        }
        // An XML discovery pipeline has an indexing template's shape (XMLParser, XSLT, indexing filter): the plan
        // kept with its XSLT says it reads raw data, not Events.
        final Optional<KeptMapping.Kept> kept = KeptMapping.readMapping(xslt(xsltUuid).getDescription());
        final boolean discovery = kept.isPresent() && "index".equals(kept.get().kind())
                                  && kept.get().payload().get("discovery") != null;
        if ("indexing".equals(shape.stage()) && !discovery) {
            eventsAvailable(build, eventsStreamIds);
        }
        final String xsltElement = shape.childMustSupply().stream()
                .filter(slot -> "XSLTFilter".equals(slot.get("type")))
                .map(slot -> String.valueOf(slot.get("element")))
                .findFirst()
                .orElse("xsltFilter");
        final List<PropertyValue> properties = new ArrayList<>();
        properties.add(PropertyValue.document(xsltElement, "xslt", xsltUuid, XsltDoc.TYPE));
        final Set<Slot> openProperties = new LinkedHashSet<>();
        shape.childMustSupply().forEach(slot -> openProperties.add(new Slot(String.valueOf(slot.get("element")),
                String.valueOf(slot.get("property")))));
        final boolean lucene = FieldPlan.LUCENE.equals(shape.backend());
        MissingFields missing = new MissingFields(List.of(), null);
        if (lucene) {
            if (indexUuid == null) {
                throw new ToolRefusal("This template indexes into Lucene: give index_uuid");
            }
            missing = missingIndexFields(indexUuid, xsltUuid);
            final String element = openElement(openProperties, "index")
                    .orElseThrow(() -> new ToolRefusal("This template sets its own Lucene index: it leaves no index "
                                                       + "for the pipeline to set"));
            properties.add(PropertyValue.document(element, "index", indexUuid, LuceneIndexDoc.TYPE));
        } else {
            properties.addAll(elasticProperties(call, shape, openProperties, indexUuid));
        }
        final Map<String, Object> made = pipelines.fromTemplate(call, context, new FromTemplate(build, name,
                templateUuid, properties, "", null, List.of(), false,
                // An indexing pipeline reads Events, not the raw sample.
                true, null, null));
        final Map<String, Object> result = new LinkedHashMap<>(made);
        result.put("backend", shape.backend());
        if (lucene && !missing.missing().isEmpty()) {
            // The index lacks fields the XSLT's plan writes (made without plan=): the agreed plan supplies them.
            result.put("index_fields_added", indexFields.addLuceneFields(context.session(),
                    new DocRef(LuceneIndexDoc.TYPE, indexUuid), missing.plan()).get("added"));
        }
        return result;
    }

    /// An indexing pipeline reads Events, which raw data only has once an events pipeline has translated it. The
    /// build's own events pipeline counts; otherwise the Events streams it will index must already exist.
    ///
    /// @param build           The build.
    /// @param eventsStreamIds The Events streams given, or none.
    /// @throws ToolRefusal if the build has no events pipeline and the streams given aren't Events (or none are).
    void eventsAvailable(final String build, final List<Long> eventsStreamIds) {
        for (final BuildDoc doc : guard.folderContents(build)) {
            if (PipelineDoc.TYPE.equals(doc.type())
                && "translation".equals(pipelineShapes.shape(doc.uuid()).stage())) {
                return;
            }
        }
        if (eventsStreamIds.isEmpty()) {
            throw new ToolRefusal("Build '" + build + "' has no events pipeline, and no events_stream_ids were "
                                  + "given. An indexing pipeline reads Events streams, not raw data: build the "
                                  + "events pipeline first (stage 1: feed, translation XSLT, step, process), then "
                                  + "index its Events. To index Events an existing pipeline already produces, pass "
                                  + "their stream ids as events_stream_ids. Raw structured data indexed as it is, "
                                  + "with no translation, is a discovery template (find_pipeline_templates "
                                  + "stage=discovery).");
        }
        inputs.requireEvents(eventsStreamIds);
    }

    /// The fields the indexing XSLT writes that the Lucene index lacks, and the field plan kept with the XSLT
    /// (null when it was written by hand). Stroom drops a value for a field the index lacks with only a warning
    /// ("Attempt to index unknown field"), so the pipeline runs and indexes nothing searchable.
    ///
    /// @param indexUuid The Lucene Index doc.
    /// @param xsltUuid  The indexing XSLT.
    /// @return The missing fields, in the order the XSLT writes them, and the plan kept with it.
    /// @throws ToolRefusal if an XSLT written by hand writes fields the index lacks, or a doc can't be read.
    MissingFields missingIndexFields(final String indexUuid, final String xsltUuid) {
        final LuceneIndexDoc index = documentAccess.read(new DocRef(LuceneIndexDoc.TYPE, indexUuid),
                        LuceneIndexDoc.class)
                .orElseThrow(() -> new ToolRefusal("Index " + indexUuid + " can't be read: it doesn't exist, or you "
                                                   + "don't have permission to view it"));
        final DocRef ref = new DocRef(LuceneIndexDoc.TYPE, indexUuid, index.getName());
        final Set<String> have = new LinkedHashSet<>();
        indexFields.indexFields(ref).forEach(field -> have.add(String.valueOf(field.get("name"))));
        final XsltDoc xslt = xslt(xsltUuid);
        final FieldPlan kept = KeptMapping.readMapping(xslt.getDescription())
                .filter(mapping -> "index".equals(mapping.kind()))
                .map(mapping -> keptPlan(mapping.payload()))
                .orElse(null);
        final List<String> written;
        if (kept != null) {
            written = kept.fields().stream().map(PlannedField::name).toList();
        } else {
            // Written by hand: the records:2 data elements it writes.
            written = dataNames(NullSafe.string(xslt.getData()));
        }
        final List<String> missing = written.stream()
                .filter(field -> !have.contains(field))
                .toList();
        if (!missing.isEmpty() && kept == null) {
            throw new ToolRefusal("Index '" + index.getName() + "' has " + (have.isEmpty()
                    ? "no fields"
                    : "no field") + " for " + PyText.list(missing.subList(0,
                    Math.min(MISSING_NAMED, missing.size()))) + (missing.size() > MISSING_NAMED
                    ? " ..."
                    : "") + ", which the indexing XSLT writes: Stroom would drop those values ('Attempt to "
                                  + "index unknown field') and the searches would find nothing. Save the indexing "
                                  + "XSLT from its plan (save_xslt index_plan=the plan from draft_index_mapping, no "
                                  + "code): the plan is kept with it, and its fields are then added to the index "
                                  + "here. Or create the index with them: create_index_doc plan=....");
        }
        return new MissingFields(missing, kept);
    }

    // Elasticsearch: the index name and, where the template leaves it open or the index doc's differs from the
    // template's, the cluster.
    private List<PropertyValue> elasticProperties(final ToolCall call,
                                                  final Shape shape,
                                                  final Set<Slot> openProperties,
                                                  final String indexUuid) {
        String indexName = stringArg(call, INDEX_NAME_ARG);
        String clusterUuid = stringArg(call, CLUSTER_UUID_ARG);
        if (indexUuid != null) {
            // The Elastic Index doc names the index and its cluster (seen in VS Code: refused for want of
            // index_name though the doc was given).
            final ElasticIndexDoc elastic = elasticIndex(indexUuid);
            final String named = elastic.getIndexName();
            if (indexName != null && NullSafe.isNonEmptyString(named)
                && !IndexingHelpers.writesToIndex(indexName, named)) {
                // Seen in testing: the index doc named as the user said, the pipeline the plan's name, and the
                // index template made for the pipeline's, so for an index Stroom doesn't search.
                throw new ToolRefusal("Elastic Index doc '" + elastic.getName() + "' names index '" + named
                                      + "', and index_name '" + indexName + "' doesn't write to it: leave "
                                      + "index_name out (the doc's is used). If the user wants '" + indexName
                                      + "', rename_index index_uuid=" + indexUuid + " index_name='" + indexName
                                      + "' first.");
            }
            if (indexName == null && NullSafe.isNonEmptyString(named)) {
                indexName = named;
            }
            if (clusterUuid == null && elastic.getClusterRef() != null) {
                clusterUuid = elastic.getClusterRef().getUuid();
            }
        }
        if (indexName == null) {
            throw new ToolRefusal("This template indexes into Elasticsearch: give index_name, or index_uuid (the "
                                  + "Elastic Index doc, which names it)");
        }
        final String element = openElement(openProperties, "indexName").orElse("elasticIndexingFilter");
        final List<PropertyValue> properties = new ArrayList<>();
        properties.add(PropertyValue.plain(element, "indexName", indexName));
        final String inherited = inheritedCluster(shape, element);
        if (openProperties.contains(new Slot(element, "cluster"))) {
            if (clusterUuid == null) {
                throw new ToolRefusal("The template leaves the cluster open: give cluster_uuid");
            }
            properties.add(PropertyValue.document(element, "cluster", clusterUuid, ElasticClusterDoc.TYPE));
        } else if (clusterUuid != null && inherited != null && !inherited.equals(clusterUuid)) {
            // Seen in Ask Stroom AI: the index doc on ES_DEV, the user's choice, and the pipeline left on the
            // template's ES_PROD, so its index template was drafted for production.
            properties.add(PropertyValue.document(element, "cluster", clusterUuid, ElasticClusterDoc.TYPE));
        }
        return properties;
    }

    // The UUID of the cluster the template sets on the element, or null.
    private static String inheritedCluster(final Shape shape, final String element) {
        return NullSafe.list(shape.shared()).stream()
                .filter(slot -> element.equals(slot.get("element")) && "cluster".equals(slot.get("property")))
                .map(slot -> slot.get("value") instanceof final Map<?, ?> value && value.get("uuid") != null
                        ? String.valueOf(value.get("uuid"))
                        : null)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private static Optional<String> openElement(final Set<Slot> openProperties, final String property) {
        return openProperties.stream()
                .filter(slot -> property.equals(slot.property()))
                .map(Slot::element)
                .findFirst();
    }

    private XsltDoc xslt(final String uuid) {
        return documentAccess.read(new DocRef(XsltDoc.TYPE, uuid), XsltDoc.class)
                .orElseThrow(() -> new ToolRefusal("XSLT " + uuid + " can't be read: it doesn't exist, or you don't "
                                                   + "have permission to view it"));
    }

    private ElasticIndexDoc elasticIndex(final String uuid) {
        Optional<ElasticIndexDoc> doc;
        try {
            doc = documentAccess.read(new DocRef(ElasticIndexDoc.TYPE, uuid), ElasticIndexDoc.class);
        } catch (final RuntimeException e) {
            doc = Optional.empty();
        }
        return doc.orElseThrow(() -> new ToolRefusal("index_uuid " + uuid + " is not an Elastic Index doc this "
                                                     + "template can index into: give index_name (and "
                                                     + "cluster_uuid)"));
    }

    private static FieldPlan keptPlan(final Map<String, Object> payload) {
        try {
            return FieldPlan.fromJson(payload);
        } catch (final RuntimeException e) {
            throw new ToolRefusal("The field plan kept with the indexing XSLT isn't valid: " + e.getMessage());
        }
    }

    private static List<String> dataNames(final String xslt) {
        final Set<String> names = new LinkedHashSet<>();
        final Matcher matcher = DATA_NAME.matcher(xslt);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return List.copyOf(names);
    }

    private String required(final ToolCall call, final String name) {
        final String value = stringArg(call, name);
        if (value == null) {
            throw new ToolRefusal("Give " + name);
        }
        return value;
    }

    /// The fields an indexing XSLT writes that its Lucene index lacks.
    ///
    /// @param missing The fields, in the order the XSLT writes them.
    /// @param plan    The field plan kept with the XSLT, or null for one written by hand.
    record MissingFields(List<String> missing, FieldPlan plan) {

    }

    // A property a template leaves open, for a child to set.
    private record Slot(String element, String property) {

    }
}
