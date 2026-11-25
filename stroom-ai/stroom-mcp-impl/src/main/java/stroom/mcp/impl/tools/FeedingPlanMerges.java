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

import stroom.ai.impl.tools.JsonValues;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.indexing.FieldPlan;
import stroom.ai.impl.tools.indexing.PlannedField;
import stroom.docref.DocRef;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/// An index template for an Elasticsearch index fed by several pipelines merges every feeding pipeline's index
/// plan (decided with the user, 2026-11-25): one index maps each field one way, so the template must hold the
/// fields every pipeline writes. Where two plans give one field different types, the template is refused, naming
/// the field and both pipelines, as no one mapping serves both.
class FeedingPlanMerges {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(FeedingPlanMerges.class);

    private final IndexSurveys indexSurveys;

    /// @param indexSurveys Finds the pipelines writing to an index, with their plans.
    @Inject
    FeedingPlanMerges(final IndexSurveys indexSurveys) {
        this.indexSurveys = indexSurveys;
    }

    /// The plan for an index's template: the pipeline's own, with the fields the other pipelines writing to the
    /// index add (their plans as kept with their indexing XSLTs).
    ///
    /// @param pipelineUuid The pipeline the template is proposed for.
    /// @param plan         Its plan.
    /// @param indexName    The Elasticsearch index it writes to.
    /// @return The merged plan, and the other pipelines merged from.
    /// @throws ToolRefusal if two plans give a field different types.
    Merged forIndex(final String pipelineUuid, final FieldPlan plan, final String indexName) {
        final List<Feeder> others = new ArrayList<>();
        String own = "this pipeline";
        try {
            for (final Map<String, Object> fed : indexSurveys.feedingPipelines(new DocRef(ElasticIndexDoc.TYPE,
                    "", indexName), indexName)) {
                final String uuid = String.valueOf(fed.get("uuid"));
                final String name = String.valueOf(fed.get("name"));
                if (uuid.equals(pipelineUuid)) {
                    own = name;
                    continue;
                }
                final Map<String, Object> kept = JsonValues.mapOrEmpty(fed.get("plan"));
                if (!kept.isEmpty()) {
                    others.add(new Feeder(name, uuid, FieldPlan.fromJson(kept)));
                }
            }
        } catch (final ToolRefusal e) {
            throw e;
        } catch (final RuntimeException e) {
            // The other pipelines unread: the template is the pipeline's own plan's, as before.
            LOGGER.debug(() -> LogUtil.message("The pipelines writing to {} weren't read: {}", indexName,
                    e.getMessage()), e);
        }
        return merge(plan, own, others, indexName);
    }

    /// @param own       The pipeline's own plan.
    /// @param ownName   The pipeline's name.
    /// @param others    The other pipelines writing to the index, with their plans.
    /// @param indexName The index, for what is said.
    /// @return The own plan with every other plan's fields it lacks, after its own; and the others merged from.
    /// @throws ToolRefusal if two plans give a field different types, naming the field and both pipelines.
    static Merged merge(final FieldPlan own, final String ownName, final List<Feeder> others, final String indexName) {
        if (NullSafe.isEmptyCollection(others)) {
            return new Merged(own, List.of());
        }
        // Each field's type, and the pipeline whose plan gave it first.
        final Map<String, Typed> typed = new LinkedHashMap<>();
        own.leafFields().forEach(field -> typed.putIfAbsent(field.name(), new Typed(type(field), ownName)));
        final List<PlannedField> fields = new ArrayList<>(own.fields());
        final Set<String> named = new LinkedHashSet<>();
        own.fields().forEach(field -> named.add(field.name()));
        final List<String> from = new ArrayList<>();
        for (final Feeder other : others) {
            for (final PlannedField leaf : other.plan().leafFields()) {
                final Typed known = typed.putIfAbsent(leaf.name(), new Typed(type(leaf), other.name()));
                if (known != null && !known.type().isEmpty() && !type(leaf).isEmpty()
                    && !known.type().equals(type(leaf))) {
                    throw new ToolRefusal("Field '" + leaf.name() + "' is " + known.type() + " in the index plan of '"
                                          + known.pipeline() + "' but " + type(leaf) + " in the plan of '" + other.name()
                                          + "', and both write to index '" + indexName + "': one index maps a field "
                                          + "one way, so no template serves both. Ask the user which type is meant "
                                          + "and change the other pipeline's plan to it (draft_index_mapping, then "
                                          + "save its indexing XSLT), or write the pipelines to separate indexes.");
                }
            }
            for (final PlannedField field : other.plan().fields()) {
                if (named.add(field.name())) {
                    fields.add(field);
                }
            }
            if (!from.contains(other.name())) {
                from.add(other.name());
            }
        }
        return new Merged(own.withFields(fields), from);
    }

    // A field's type as an index maps it: case aside; "" for none.
    private static String type(final PlannedField field) {
        return NullSafe.string(field.type()).strip().toLowerCase(Locale.ROOT);
    }


    // --------------------------------------------------------------------------------


    // A field's type, and the pipeline whose plan gave it.
    private record Typed(String type, String pipeline) {

    }


    // --------------------------------------------------------------------------------


    /// Another pipeline writing to the index.
    ///
    /// @param name Its name.
    /// @param uuid Its UUID.
    /// @param plan The plan kept with its indexing XSLT.
    record Feeder(String name, String uuid, FieldPlan plan) {

    }


    // --------------------------------------------------------------------------------


    /// A plan merged from every pipeline writing to an index.
    ///
    /// @param plan       The merged plan.
    /// @param mergedFrom The other pipelines whose plans were merged in, by name; empty for none.
    record Merged(FieldPlan plan, List<String> mergedFrom) {

    }
}
