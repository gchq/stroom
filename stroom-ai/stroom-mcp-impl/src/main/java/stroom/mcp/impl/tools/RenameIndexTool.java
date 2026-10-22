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
import stroom.ai.impl.tools.ProcessorFilterAccess;
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolDefinition;
import stroom.ai.impl.tools.ToolPermissions;
import stroom.ai.impl.tools.ToolResult;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.impl.tools.indexing.IndexingHelpers;
import stroom.ai.impl.tools.indexing.IndexingHelpers.Destination;
import stroom.ai.shared.AiToolCategory;
import stroom.docref.DocRef;
import stroom.docstore.api.DocumentActionHandler;
import stroom.pipeline.shared.PipelineDoc;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/// `rename_index` - another Elasticsearch index name wherever the build uses one, when the user asks for it (the
/// Stroom MCP server's `tools/indexing.py` `rename_index`): the build's Elastic Index doc and each of the build's
/// indexing pipelines writing to it, together, once the user confirms in a form. The reply gives what the cluster
/// admin removes for the old name; nothing is written to Elasticsearch.
class RenameIndexTool extends AbstractBuiltInTool {

    static final String NAME = "rename_index";
    static final String INDEX_UUID_ARG = "index_uuid";
    static final String INDEX_NAME_ARG = "index_name";
    static final String CONFIRMATION_ID_ARG = "confirmation_id";

    private static final Pattern INDEX_NAME = Pattern.compile("^[a-z0-9][a-z0-9._-]*$");

    private final DocumentAccess documentAccess;
    private final WriteGuard guard;
    private final BuildIndexes buildIndexes;
    private final PipelineDocs pipelineDocs;
    private final ProcessorFilterAccess processorFilterAccess;
    private final Consent consent;
    private final BuildPlan plan;

    @Inject
    RenameIndexTool(final DocumentAccess documentAccess,
                    final WriteGuard guard,
                    final BuildIndexes buildIndexes,
                    final PipelineDocs pipelineDocs,
                    final ProcessorFilterAccess processorFilterAccess,
                    final Consent consent,
                    final BuildPlan plan) {
        this.documentAccess = documentAccess;
        this.guard = guard;
        this.buildIndexes = buildIndexes;
        this.pipelineDocs = pipelineDocs;
        this.processorFilterAccess = processorFilterAccess;
        this.consent = consent;
        this.plan = plan;
    }

    @Override
    public AiToolCategory category() {
        return AiToolCategory.INDEXING;
    }

    @Override
    public boolean isReadOnly() {
        // It changes the index doc and the build's indexing pipelines.
        return false;
    }

    @Override
    public boolean confirmsItself() {
        // The user confirms the new name, and what changes with it, in a form.
        return true;
    }

    @Override
    public ToolPermissions permissions() {
        return ToolPermissions.builder()
                .checksDocuments(DocumentPermission.VIEW, DocumentPermission.EDIT)
                .note("Only the build's own Elastic Index doc and indexing pipelines are changed, as the user, who "
                      + "needs Edit on each. Nothing is written to Elasticsearch: what the old name left there is "
                      + "for the cluster admin.")
                .build();
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(NAME)
                .description("Use another Elasticsearch index name wherever the build uses one, when the user asks "
                             + "for it: the Elastic Index doc's index name and that of each of the build's indexing "
                             + "pipelines writing to it, together. Only in a build. The template agreed for the old "
                             + "name doesn't apply to the new one: propose_index_template again (its name and index "
                             + "pattern follow the new name). The user confirms in a form; the reply gives what the "
                             + "cluster admin removes for the old name (its index template, and an index this build "
                             + "wrote to).")
                .parameters(ObjectSchema.builder()
                        .addStringProperty(INDEX_UUID_ARG, "The build's Elastic Index doc.")
                        .addStringProperty(INDEX_NAME_ARG, "The index or data stream name the user wants instead.")
                        .addStringProperty(CONFIRMATION_ID_ARG, "From an earlier needs_confirmation reply.")
                        .required(INDEX_UUID_ARG, INDEX_NAME_ARG)
                        .build())
                .build();
    }

    @Override
    public ToolResult execute(final ToolCall call, final ToolCallContext context) {
        try {
            final Map<String, Object> result = rename(call, context);
            return UserActionEntries.withActions(JsonResults.of(result), result);
        } catch (final ToolRefusal e) {
            return JsonResults.refused(e);
        }
    }

    private Map<String, Object> rename(final ToolCall call, final ToolCallContext context) {
        final String indexUuid = required(call, INDEX_UUID_ARG);
        final String wanted = NullSafe.string(rawArg(call, INDEX_NAME_ARG));
        final ElasticIndexDoc doc = documentAccess.read(new DocRef(ElasticIndexDoc.TYPE, indexUuid),
                        ElasticIndexDoc.class)
                .orElseThrow(() -> new ToolRefusal(indexUuid + " is not an Elastic Index doc: give the build's "
                                                   + "(find_documents type ElasticIndex). A Lucene index is named by "
                                                   + "its doc: nothing to rename."));
        final String build = guard.checkInBuild(context.session(), new DocRef(ElasticIndexDoc.TYPE, indexUuid,
                doc.getName()));
        final String newName = wanted.strip();
        final String old = NullSafe.string(doc.getIndexName());
        if (!INDEX_NAME.matcher(newName).matches() || ".".equals(newName) || "..".equals(newName)) {
            throw new ToolRefusal("'" + newName + "' is not an Elasticsearch index name: lowercase letters, digits, "
                                  + "'-', '_' and '.', not starting with '-', '_' or '+'; no wildcard, space or comma");
        }
        if (newName.equals(old)) {
            final Map<String, Object> result = new LinkedHashMap<>();
            result.put("type", ElasticIndexDoc.TYPE);
            result.put("uuid", indexUuid);
            result.put("name", doc.getName());
            result.put("index name", newName);
            result.put("hint", "The doc already names '" + newName + "': nothing changed.");
            return result;
        }
        final List<Destination> pipelines = buildIndexes.destinations(build).stream()
                .filter(p -> !old.isEmpty() && IndexingHelpers.writesToIndex(p.indexName(), old))
                .toList();
        BuildIndexes.refuseBuiltNames(pipelines);
        final List<String> removals = removals(pipelines, old);
        final Map<String, Object> details = new LinkedHashMap<>();
        details.put("build", build);
        details.put("index doc", doc.getName());
        details.put("index name", "'" + old + "', to be '" + newName + "'");
        details.put("indexing pipelines changed with it", pipelines.isEmpty()
                ? "none in the build yet"
                : pipelines.stream().map(Destination::name).toList());
        details.put("index template", "proposed again for '" + newName + "': the one agreed was for '" + old + "'");
        if (!removals.isEmpty()) {
            details.put("for the cluster admin, once they no longer want them", removals);
        }
        final Optional<Map<String, Object>> gate = consent.require(call, context, Consent.Request.confirmation(NAME,
                "Use index name '" + newName + "' instead of '" + old + "'", details,
                stringArg(call, CONFIRMATION_ID_ARG)));
        if (gate.isPresent()) {
            return gate.get();
        }
        final ElasticIndexDoc saved = write(doc.copy().indexName(newName).build());
        for (final Destination pipeline : pipelines) {
            buildIndexes.setIndexName(context.session(), pipeline, newName);
        }
        final List<Map<String, Object>> buttons = new ArrayList<>();
        for (int n = 0; n < removals.size(); n++) {
            buttons.add(UserActionEntries.esRequest("remove-old-" + (n + 1), removals.get(n),
                    "No longer used: the build now writes to '" + newName + "'"));
        }
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", ElasticIndexDoc.TYPE);
        result.put("uuid", saved.getUuid());
        result.put("name", saved.getName());
        result.put("index name", newName);
        result.put("was", old);
        result.put("pipelines", pipelines.stream().map(Destination::name).toList());
        if (!removals.isEmpty()) {
            result.put("for_the_cluster_admin", removals);
        }
        UserActionEntries.withEntries(result, buttons);
        result.put("hint", "The build now uses '" + newName + "'. propose_index_template again for it (its name and "
                           + "index pattern follow the new name), with the user's example, and have them confirm it; "
                           + "then index as before." + (removals.isEmpty()
                ? ""
                : " Give the user for_the_cluster_admin: what the old name left on the cluster."));
        return plan.withNext(context, build, result);
    }

    // What the old name left on the cluster: the template agreed for it (one the tools proposed), and an index this
    // build's pipelines wrote to.
    private List<String> removals(final List<Destination> pipelines, final String old) {
        final Set<String> removals = new LinkedHashSet<>();
        for (final Destination pipeline : pipelines) {
            final PipelineDoc held = pipelineDocs.read(pipeline.uuid());
            final Map<String, Object> agreed = KeptMapping.readAgreedTemplate(held.getDescription()).orElse(Map.of());
            if (agreed.get("name") != null && !"".equals(agreed.get("name"))
                && !Boolean.TRUE.equals(agreed.get("by_user"))) {
                removals.add("DELETE _index_template/" + agreed.get("name"));
            }
            final ProcessorFilterAccess.Filters filters = processorFilterAccess.filtersOf(new DocRef(PipelineDoc.TYPE,
                    pipeline.uuid(), pipeline.name()));
            if (filters != null && !NullSafe.list(filters.filters()).isEmpty()) {
                removals.add(IndexingRuns.dropRequest(held, old));
            }
        }
        return new ArrayList<>(removals);
    }

    @SuppressWarnings("unchecked")
    private ElasticIndexDoc write(final ElasticIndexDoc doc) {
        final DocumentActionHandler<ElasticIndexDoc> handler = documentAccess.handler(ElasticIndexDoc.TYPE)
                .orElseThrow(() -> new ToolRefusal("Stroom has no Elastic Index store"));
        return handler.writeDocument(doc);
    }

    private String required(final ToolCall call, final String name) {
        final String value = stringArg(call, name);
        if (value == null) {
            throw new ToolRefusal("Give " + name);
        }
        return value;
    }
}
