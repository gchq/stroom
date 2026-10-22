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

import stroom.ai.api.ElasticClusterTests;
import stroom.ai.api.ElasticClusterTests.TestResult;
import stroom.ai.impl.tools.AbstractBuiltInTool;
import stroom.ai.impl.tools.DocumentAccess;
import stroom.ai.impl.tools.PyText;
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolDefinition;
import stroom.ai.impl.tools.ToolPermissions;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.ToolResult;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.impl.tools.indexing.FieldPlan;
import stroom.ai.impl.tools.indexing.FieldPlans;
import stroom.ai.impl.tools.indexing.IndexingHelpers;
import stroom.ai.impl.tools.indexing.IndexingHelpers.Destination;
import stroom.ai.shared.AiToolCategory;
import stroom.docref.DocRef;
import stroom.docstore.api.DocumentActionHandler;
import stroom.docstore.shared.AbstractDoc;
import stroom.index.shared.LuceneIndexDoc;
import stroom.index.shared.LuceneIndexDoc.PartitionBy;
import stroom.search.elastic.shared.ElasticClusterDoc;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.security.shared.DocumentPermission;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/// `create_index_doc` - creates the build's index doc (the Stroom MCP server's `tools/indexing.py`
/// `create_index_doc`, with `set_index_fields` and `test_elastic_index`): a Lucene Index in a volume group with the
/// plan's fields, or an Elastic Index doc on an existing Elastic Cluster doc pointing at the index or data stream,
/// tested against the cluster. The user confirms the backend, name and target, and may correct the names.
class CreateIndexDocTool extends AbstractBuiltInTool {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(CreateIndexDocTool.class);

    static final String NAME = "create_index_doc";
    static final String BUILD_ARG = "build";
    static final String BACKEND_ARG = "backend";
    static final String NAME_ARG = "name";
    static final String TIME_FIELD_ARG = "time_field";
    static final String INDEX_NAME_ARG = "index_name";
    static final String CLUSTER_UUID_ARG = "cluster_uuid";
    static final String VOLUME_GROUP_ARG = "volume_group";
    static final String PLAN_ARG = "plan";
    static final String CONFIRMATION_ID_ARG = "confirmation_id";
    static final String DEFAULT_VOLUME_GROUP = "Default Volume Group";

    private final IndexPlans plans;
    private final BuildIndexes buildIndexes;
    private final IndexFields indexFields;
    private final WriteGuard guard;
    private final DocumentAccess documentAccess;
    private final Consent consent;
    private final BuildPlan plan;
    private final Provider<ElasticClusterTests> elasticClusterTestsProvider;

    @Inject
    CreateIndexDocTool(final IndexPlans plans,
                       final BuildIndexes buildIndexes,
                       final IndexFields indexFields,
                       final WriteGuard guard,
                       final DocumentAccess documentAccess,
                       final Consent consent,
                       final BuildPlan plan,
                       final Provider<ElasticClusterTests> elasticClusterTestsProvider) {
        this.plans = plans;
        this.buildIndexes = buildIndexes;
        this.indexFields = indexFields;
        this.guard = guard;
        this.documentAccess = documentAccess;
        this.consent = consent;
        this.plan = plan;
        this.elasticClusterTestsProvider = elasticClusterTestsProvider;
    }

    @Override
    public AiToolCategory category() {
        return AiToolCategory.INDEXING;
    }

    @Override
    public boolean isReadOnly() {
        // It creates an index doc.
        return false;
    }

    @Override
    public boolean confirmsItself() {
        // The user confirms (or corrects) the doc's name and index name in a form before it is made.
        return true;
    }

    @Override
    public ToolPermissions permissions() {
        return ToolPermissions.builder()
                .checksDocuments(DocumentPermission.VIEW, DocumentPermission.EDIT)
                .note("The index doc is created in the build folder as the user, who needs Create there and Edit on "
                      + "it; a Lucene index's fields are added through Stroom's index service. An Elastic Index doc "
                      + "uses an existing Elastic Cluster doc the user may view, whose connection is tested; nothing "
                      + "is written to Elasticsearch.")
                .build();
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(NAME)
                .description(BuildPlan.onboardingLabel(NAME)
                             + "Create the build's index doc: a Lucene Index in a volume group with the plan's fields, "
                             + "or an Elastic Index doc on an existing Elastic Cluster doc pointing at the index or "
                             + "data stream, tested against the cluster. The user confirms the backend, name and "
                             + "target.")
                .parameters(ObjectSchema.builder()
                        .addStringProperty(BUILD_ARG, CreateFeedTool.BUILD_DESCRIPTION)
                        .addEnumProperty(BACKEND_ARG, FieldPlans.BACKENDS, "The index's backend.")
                        .addStringProperty(NAME_ARG, "Index doc name, following the environment's convention.")
                        .addStringProperty(TIME_FIELD_ARG, "The plan's time field.")
                        .addStringProperty(INDEX_NAME_ARG, "Elasticsearch: the index or data stream name.")
                        .addStringProperty(CLUSTER_UUID_ARG, "Elasticsearch: an existing Elastic Cluster doc.")
                        .addStringProperty(VOLUME_GROUP_ARG, "Lucene: the index volume group.")
                        .addProperty(PLAN_ARG, ObjectSchema.builder()
                                .description("Lucene: the field plan from draft_index_mapping; its fields are added "
                                             + "to the index doc (keywords as TEXT with the KEYWORD analyzer). "
                                             + "Elasticsearch fields come from the index template. Omitted: the one "
                                             + "drafted for this index in the session.")
                                .build())
                        .addStringProperty(CONFIRMATION_ID_ARG, "From an earlier needs_confirmation reply.")
                        .required(BUILD_ARG, BACKEND_ARG, NAME_ARG, TIME_FIELD_ARG)
                        .build())
                .build();
    }

    @Override
    public ToolResult execute(final ToolCall call, final ToolCallContext context) {
        try {
            return JsonResults.of(create(call, context));
        } catch (final ToolRefusal e) {
            return JsonResults.refused(e);
        }
    }

    private Map<String, Object> create(final ToolCall call, final ToolCallContext context) {
        final String build = required(call, BUILD_ARG);
        final String backend = required(call, BACKEND_ARG);
        if (!FieldPlans.BACKENDS.contains(backend)) {
            throw new ToolRefusal("backend is 'lucene' or 'elasticsearch', not '" + backend + "'");
        }
        final String proposedName = required(call, NAME_ARG);
        final String timeField = required(call, TIME_FIELD_ARG);
        final String volumeGroup = NullSafe.nonBlankStringElse(stringArg(call, VOLUME_GROUP_ARG),
                DEFAULT_VOLUME_GROUP);
        String indexName = stringArg(call, INDEX_NAME_ARG);
        FieldPlan fieldPlan = IndexingArgs.plan(call, PLAN_ARG);
        if (fieldPlan == null) {
            // The plan drafted for this index in the session: not pasted again (seen: a 50-field plan, five times).
            final List<String> names = new ArrayList<>();
            names.add(indexName);
            names.add(proposedName);
            final FieldPlan drafted = plans.given(null, names, null);
            fieldPlan = drafted != null && backend.equals(drafted.backend())
                    ? drafted
                    : null;
        }
        final boolean elastic = FieldPlan.ELASTICSEARCH.equals(backend);
        final Map<String, Object> target = new LinkedHashMap<>();
        ElasticClusterDoc cluster = null;
        if (elastic) {
            // Seen in VS Code: refused for want of both, though the plan names the index and every Elastic Index
            // doc in the environment used the same cluster. Both are shown in the user's form.
            if (indexName == null && fieldPlan != null) {
                indexName = fieldPlan.indexName();
            }
            String clusterUuid = stringArg(call, CLUSTER_UUID_ARG);
            String why = "";
            if (clusterUuid == null) {
                clusterUuid = buildIndexes.clusterInUse().map(DocRef::getUuid).orElse(null);
                why = clusterUuid != null
                        ? " (the cluster the existing Elastic Index docs use)"
                        : "";
            }
            if (indexName == null || clusterUuid == null) {
                throw new ToolRefusal("Elasticsearch needs index_name (or plan=, which names it) and cluster_uuid: the "
                                      + "existing Elastic Index docs use more than one cluster, or none "
                                      + "(find_elastic_clusters lists them)");
            }
            cluster = cluster(clusterUuid);
            target.put("cluster", cluster.getName() + why);
            target.put("index name", indexName);
            refuseUnnamedPipeline(build, indexName);
        } else {
            target.put("volume group", volumeGroup);
        }
        final Map<String, Object> details = new LinkedHashMap<>();
        details.put("build", build);
        details.put("backend", backend);
        details.put("index doc", proposedName);
        details.put("time field", timeField);
        details.putAll(target);
        Consent.Request request = Consent.Request.confirmation(NAME, "Create " + backend + " index doc '"
                                                                     + proposedName + "'", details,
                        stringArg(call, CONFIRMATION_ID_ARG))
                .withEditable(NAME_ARG, "Index doc name", proposedName);
        if (elastic) {
            request = request.withEditable(INDEX_NAME_ARG, "Elasticsearch index name", indexName);
        }
        final Optional<Map<String, Object>> gate = consent.require(call, context, request);
        if (gate.isPresent()) {
            return gate.get();
        }
        // The user may have corrected the names in the form.
        final String name = consent.edited(context, NAME_ARG, proposedName);
        final String proposed = indexName;
        if (elastic) {
            indexName = consent.edited(context, INDEX_NAME_ARG, indexName);
        }
        // The user gave another name in the form: the build's indexing pipelines writing to the one proposed take
        // it too (asked for by the user: a name the user changes is changed everywhere it is used).
        final List<Destination> follow = elastic && !indexName.equals(proposed)
                ? buildIndexes.destinations(build).stream()
                        .filter(p -> IndexingHelpers.writesToIndex(p.indexName(), proposed))
                        .toList()
                : List.of();
        BuildIndexes.refuseBuiltNames(follow);
        if (elastic) {
            // The reply says what was made: seen in Ask Stroom AI, it kept the index name from before the user's
            // edit, and the agent offered that back to them as what the doc held.
            target.put("index name", indexName);
        }
        final String docType = IndexingHelpers.INDEX_TYPE.get(backend);
        final DocRef ref = guard.create(docType, name, build, null, false, IndexExamples.ROLE);
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", docType);
        result.put("uuid", ref.getUuid());
        result.put("name", ref.getName());
        result.putAll(target);
        if (elastic) {
            final ElasticIndexDoc doc = write(ElasticIndexDoc.class, ElasticIndexDoc.TYPE, readDoc(ref,
                    ElasticIndexDoc.class).copy()
                    .clusterRef(new DocRef(ElasticClusterDoc.TYPE, cluster.getUuid(), cluster.getName()))
                    .indexName(indexName)
                    .timeField(timeField)
                    .build());
            result.put("name", doc.getName());
            for (final Destination pipeline : follow) {
                buildIndexes.setIndexName(context.session(), pipeline, indexName);
            }
            if (!follow.isEmpty()) {
                result.put("pipelines_renamed", PyText.list(follow.stream().map(Destination::name).toList())
                                                + " now write to '" + indexName + "' too (they wrote to '" + proposed
                                                + "'): its index template is agreed for '" + indexName + "'");
            }
            result.put("test", test(cluster));
        } else {
            final LuceneIndexDoc doc = write(LuceneIndexDoc.class, LuceneIndexDoc.TYPE, readDoc(ref,
                    LuceneIndexDoc.class).copy()
                    .volumeGroupName(volumeGroup)
                    .timeField(timeField)
                    .partitionBy(PartitionBy.MONTH)
                    .partitionSize(1)
                    .shardsPerPartition(1)
                    .build());
            result.put("name", doc.getName());
            if (fieldPlan != null) {
                result.put("fields", indexFields.addLuceneFields(context.session(), ref, fieldPlan));
            } else {
                result.put("fields", "none yet: the index indexes nothing until it has the plan's fields. Give plan= "
                                     + "here, or create_indexing_pipeline adds them from the plan kept with the "
                                     + "indexing XSLT (save_xslt index_plan=...).");
            }
        }
        return plan.withNext(context, build, result);
    }

    // Refused for a name none of the build's indexing pipelines writes to while one writes to no doc yet: that
    // pipeline's doc is the one meant (seen in testing: the doc named one way, the pipeline another).
    private void refuseUnnamedPipeline(final String build, final String indexName) {
        final List<Destination> writing = buildIndexes.destinations(build);
        final List<String> docs = writing.isEmpty()
                ? List.of()
                : List.copyOf(buildIndexes.indexDocs(build).values());
        final List<Destination> waiting = writing.stream()
                .filter(p -> docs.stream().noneMatch(n -> IndexingHelpers.writesToIndex(p.indexName(), n)))
                .toList();
        if (!waiting.isEmpty() && writing.stream()
                .noneMatch(p -> IndexingHelpers.writesToIndex(p.indexName(), indexName))) {
            throw new ToolRefusal("The build's indexing pipeline '" + waiting.getFirst().name() + "' writes to '"
                                  + waiting.getFirst().indexName() + "', and no index doc names it yet: give "
                                  + "index_name='" + waiting.getFirst().indexName() + "'. For another name the user "
                                  + "wants, create the doc with that one and then rename_index: it changes both.");
        }
    }

    private ElasticClusterDoc cluster(final String clusterUuid) {
        final Optional<ElasticClusterDoc> cluster = documentAccess.read(new DocRef(ElasticClusterDoc.TYPE,
                clusterUuid), ElasticClusterDoc.class);
        if (cluster.isPresent()) {
            return cluster.get();
        }
        // Seen: an Elastic Index doc's uuid (the index the user had been offered to follow) given as the cluster,
        // and Stroom's 500 "Document not found" passed on as it was.
        final Optional<ElasticIndexDoc> index = documentAccess.read(new DocRef(ElasticIndexDoc.TYPE, clusterUuid),
                ElasticIndexDoc.class);
        if (index.isPresent()) {
            final DocRef its = index.get().getClusterRef();
            throw new ToolRefusal(clusterUuid + " is the Elastic Index doc '" + index.get().getName() + "', not an "
                                  + "Elastic Cluster: its cluster is '" + NullSafe.get(its, DocRef::getName)
                                  + "' (cluster_uuid=" + NullSafe.get(its, DocRef::getUuid) + "). Give that, or "
                                  + "another from find_elastic_clusters.");
        }
        throw new ToolRefusal("No Elastic Cluster doc " + clusterUuid + ": find_elastic_clusters lists them.");
    }

    // Stroom's own connection test of the doc's cluster, as its Test Connection button does.
    private Map<String, Object> test(final ElasticClusterDoc cluster) {
        final Map<String, Object> outcome = new LinkedHashMap<>();
        try {
            final TestResult tested = elasticClusterTestsProvider.get().test(cluster);
            final Map<String, Object> result = new LinkedHashMap<>();
            result.put("ok", tested.ok());
            result.put("message", tested.message());
            outcome.put("result", result);
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> "Unable to test cluster " + cluster.getUuid(), e);
            outcome.put("error", PipelineShapes.reason(e));
        }
        return outcome;
    }

    private <T> T readDoc(final DocRef ref, final Class<T> type) {
        return documentAccess.read(ref, type).orElseThrow(() -> new ToolRefusal("The new " + ref.getType() + " "
                                                                                + ref.getUuid() + " can't be read"));
    }

    @SuppressWarnings("unchecked")
    private <T extends AbstractDoc> T write(final Class<T> type, final String docType, final T doc) {
        final DocumentActionHandler<AbstractDoc> handler = documentAccess.handler(docType)
                .orElseThrow(() -> new ToolRefusal("Stroom has no " + docType + " store"));
        return type.cast(handler.writeDocument(doc));
    }

    private String required(final ToolCall call, final String name) {
        final String value = stringArg(call, name);
        if (value == null) {
            throw new ToolRefusal("Give " + name);
        }
        return value;
    }
}
