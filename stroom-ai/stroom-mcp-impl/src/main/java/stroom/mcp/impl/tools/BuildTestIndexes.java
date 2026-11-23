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
import stroom.ai.impl.tools.documents.DocumentAccess;
import stroom.ai.impl.tools.documents.KeptMapping;
import stroom.ai.impl.tools.indexing.FieldPlan;
import stroom.ai.impl.tools.processing.MergedPipeline;
import stroom.ai.impl.tools.processing.MergedPipeline.ElementProperty;
import stroom.docref.DocRef;
import stroom.docstore.api.DocumentActionHandler;
import stroom.docstore.shared.AbstractDoc;
import stroom.index.shared.LuceneIndexDoc;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.data.PipelineProperty;
import stroom.pipeline.shared.data.PipelinePropertyValue;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/// The build's test index, where a build's indexing pipeline writes while the build is open, the way an events
/// pipeline writes to the build's test feed ([OutputFeeds]): a build never writes into a production index. Seen in an
/// eval (a later build on a promoted aggregated feed): a working copy of the group's indexing pipeline, processed in
/// the build, indexed its sample Events into the live production index, every document without the build's new
/// fields ("Attempt to index unknown field", the fields only added at promotion), 192 documents duplicated there and
/// no way to take them out.
///
/// A Lucene index doc outside the build, named by a build pipeline's IndexingFilter, gets a test index in the build
/// that mirrors it (its settings and fields) with the fields of the pipeline's kept index plan added, named
/// `<INDEX>-MCP-TEST` and recorded against the production index ([#TEST_INDEX_FOR]). The pipeline's index property
/// is set to it before any processing; verify_index searches it in the production index's place. Promotion never
/// promotes the test index (it holds only the sample's documents; [PromotionTidyUp] deletes its shards, then it,
/// unless the user keeps it), writes the working copy's XSLT back (the
/// working-copy pipeline itself is deleted) and adds the new fields to the production index, and points a promoted
/// pipeline back at the production index ([#restoreIndex]). An Elasticsearch index is written to by its index name on
/// the cluster, which the build can't mirror without the cluster's templates: a build pipeline writing to a
/// production index name is stepped in the build, not processed ([ProductionIndexes], refused by the processing
/// tools).
class BuildTestIndexes {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(BuildTestIndexes.class);

    /// What the tools call a build's test index: the production index doc's name with this after it.
    static final String TEST_INDEX = "-MCP-TEST";
    /// The build record's key, on a test index's entry, for the UUID of the production index doc it stands in for.
    static final String TEST_INDEX_FOR = "test_index_for";

    private static final String LUCENE_FILTER = "IndexingFilter";
    private static final String INDEX = "index";
    // The most numbered names tried for a test index in one build.
    private static final int LAST_FREE_NAME = 99;

    private final PipelineShapes pipelineShapes;
    private final PipelineDocs pipelineDocs;
    private final WriteGuard guard;
    private final BuildRecords records;
    private final DocumentAccess documentAccess;
    private final IndexFields indexFields;
    private final KeptMappings keptMappings;

    /// @param pipelineShapes Reads a pipeline as Stroom runs it.
    /// @param pipelineDocs   Reads and saves pipelines as the user.
    /// @param guard          Says which build a document is in, and makes the test index there.
    /// @param records        Records which production index a test index stands in for.
    /// @param documentAccess Reads and writes the index docs, as the user.
    /// @param indexFields    Copies a Lucene index's fields, and adds a plan's.
    /// @param keptMappings   Reads the index plan kept with the pipeline's indexing XSLT.
    @Inject
    BuildTestIndexes(final PipelineShapes pipelineShapes,
                final PipelineDocs pipelineDocs,
                final WriteGuard guard,
                final BuildRecords records,
                final DocumentAccess documentAccess,
                final IndexFields indexFields,
                final KeptMappings keptMappings) {
        this.pipelineShapes = pipelineShapes;
        this.pipelineDocs = pipelineDocs;
        this.guard = guard;
        this.records = records;
        this.documentAccess = documentAccess;
        this.indexFields = indexFields;
        this.keptMappings = keptMappings;
    }

    /// Points a build's indexing pipeline at the build's test index of each production Lucene index it names, made
    /// (or given the plan's new fields) now, before it processes anything. A test index the pipeline names already
    /// (copy_pipeline points a copy at it at once) is given the plan's new fields too: a field the plan gained since
    /// reaches it before the pipeline processes.
    ///
    /// @param pipeline A pipeline.
    /// @return The test indexes it now writes to; none for a pipeline outside any build, or one that writes only to
    /// the build's own indexes.
    List<TestIndex> redirect(final DocRef pipeline) {
        final String build = guard.buildOf(new DocRef(PipelineDoc.TYPE, pipeline.getUuid())).orElse(null);
        if (build == null) {
            return List.of();
        }
        final MergedPipeline merged = pipelineShapes.merged(pipeline.getUuid());
        final Map<String, String> production = productionIndexes(build, merged);
        final List<String> standingIn = testIndexesNamed(build, merged);
        if (production.isEmpty() && standingIn.isEmpty()) {
            return List.of();
        }
        final Optional<FieldPlan> plan = plan(pipeline.getUuid());
        final List<TestIndex> out = new ArrayList<>();
        for (final String productionUuid : standingIn) {
            out.add(testIndex(build, productionUuid, plan.orElse(null)));
        }
        PipelineDoc doc = pipelineDocs.read(pipeline.getUuid());
        boolean changed = false;
        for (final Map.Entry<String, String> entry : production.entrySet()) {
            final TestIndex test = testIndex(build, entry.getValue(), plan.orElse(null));
            doc = PipelineDocs.withProperty(doc, entry.getKey(), INDEX, new PipelinePropertyValue(
                    new DocRef(LuceneIndexDoc.TYPE, test.uuid(), test.name())));
            changed = true;
            out.add(test);
        }
        if (changed) {
            pipelineDocs.write(doc);
        }
        return out;
    }

    /// @param build     A build.
    /// @param indexUuid An index doc's UUID.
    /// @return The build's test index standing in for it; empty when it has none.
    Optional<DocRef> testIndexOf(final String build, final String indexUuid) {
        if (NullSafe.isBlankString(build) || NullSafe.isBlankString(indexUuid)) {
            return Optional.empty();
        }
        final Map<String, String> tests = testIndexes(build);
        return guard.folderContents(build).stream()
                .filter(doc -> indexUuid.equals(tests.get(doc.uuid())))
                .map(WriteGuard.BuildDoc::docRef)
                .findFirst();
    }

    /// @param build     A build.
    /// @param indexUuid An index doc's UUID: a production index, or one of the build's test indexes.
    /// @return The build's test index searched for it: the one standing in for a production index, or the test
    /// index itself; empty for any other index.
    Optional<DocRef> searchedFor(final String build, final String indexUuid) {
        final Optional<DocRef> standingIn = testIndexOf(build, indexUuid);
        if (standingIn.isPresent() || NullSafe.isBlankString(build) || !testIndexes(build).containsKey(indexUuid)) {
            return standingIn;
        }
        return guard.folderContents(build).stream()
                .filter(doc -> indexUuid.equals(doc.uuid()))
                .map(WriteGuard.BuildDoc::docRef)
                .findFirst();
    }

    /// @param build A build.
    /// @return The UUIDs of the build's test indexes, each with the production index doc's it stands in for.
    Map<String, String> testIndexes(final String build) {
        final Map<String, String> out = new LinkedHashMap<>();
        BuildRecords.docs(records.load(build)).forEach((uuid, entry) -> {
            if (entry instanceof final Map<?, ?> map && map.get(TEST_INDEX_FOR) != null) {
                out.put(uuid, String.valueOf(map.get(TEST_INDEX_FOR)));
            }
        });
        return out;
    }

    /// Points a promoted pipeline's IndexingFilter back at the production index where it names one of the build's
    /// test indexes: in production it indexes where the build's test index stood in.
    ///
    /// @param pipelineUuid The pipeline.
    /// @param tests        The build's test indexes ([#testIndexes]).
    /// @return True if it changed.
    boolean restoreIndex(final String pipelineUuid, final Map<String, String> tests) {
        if (tests.isEmpty()) {
            return false;
        }
        final PipelineDoc doc = pipelineDocs.read(pipelineUuid);
        final List<PipelineProperty> added = new ArrayList<>();
        boolean changed = false;
        for (final PipelineProperty property : PipelineDocs.addedProperties(doc)) {
            final String uuid = NullSafe.get(property.getValue(), PipelinePropertyValue::getEntity, DocRef::getUuid);
            if (INDEX.equals(property.getName()) && uuid != null && tests.containsKey(uuid)) {
                final String original = tests.get(uuid);
                final String name = documentAccess.read(new DocRef(LuceneIndexDoc.TYPE, original),
                        LuceneIndexDoc.class).map(LuceneIndexDoc::getName).orElse(null);
                added.add(new PipelineProperty(property.getElement(), INDEX, new PipelinePropertyValue(
                        new DocRef(LuceneIndexDoc.TYPE, original, name))));
                changed = true;
            } else {
                added.add(property);
            }
        }
        if (changed) {
            pipelineDocs.write(PipelineDocs.withAddedProperties(doc, added));
        }
        return changed;
    }

    /// What promoting a build adds to the production Lucene indexes its indexing pipelines write to once promoted:
    /// the fields of each pipeline's kept index plan the index lacks. For any build, an aggregated feed's or not:
    /// before, a working copy (or new version) of an indexing pipeline whose plan added a field was written back
    /// with the production index lacking it, and production indexing dropped the field's values ("Attempt to index
    /// unknown field"). A pipeline writing to an index of the build's own is left out: that index was made with its
    /// plan's fields (create_indexing_pipeline adds them).
    ///
    /// @param build   The build.
    /// @param docs    Its documents, as the plan lists them.
    /// @param handled The UUIDs of index docs whose fields promotion adds otherwise (an aggregated feed's groups'
    ///                indexes, [FeedIndexDecisions]).
    /// @return Each production index and the fields it gains; none when every index has its plan's fields.
    List<FieldAddition> promotionAdditions(final String build,
                                           final List<Map<String, Object>> docs,
                                           final Set<String> handled) {
        final Map<String, String> tests = testIndexes(build);
        final Map<String, FieldAddition> byIndex = new LinkedHashMap<>();
        for (final Map<String, Object> listed : docs) {
            if (!PipelineDoc.TYPE.equals(listed.get("type"))) {
                continue;
            }
            final String pipelineUuid = String.valueOf(listed.get("uuid"));
            final Optional<FieldPlan> plan = plan(pipelineUuid);
            if (plan.isEmpty()) {
                continue;
            }
            final MergedPipeline merged;
            try {
                merged = pipelineShapes.merged(pipelineUuid);
            } catch (final RuntimeException e) {
                LOGGER.debug(() -> LogUtil.message("Pipeline {} not read for promotion's index fields: {}",
                        pipelineUuid, e.getMessage()));
                continue;
            }
            for (final Map.Entry<String, String> element : merged.elements().entrySet()) {
                if (!LUCENE_FILTER.equals(element.getValue())) {
                    continue;
                }
                final String named = docUuid(merged.properties().get(new ElementProperty(element.getKey(), INDEX)));
                final String production = named == null
                        ? null
                        : tests.containsKey(named)
                                ? tests.get(named)
                                : inBuild(build, new DocRef(LuceneIndexDoc.TYPE, named))
                                        ? null
                                        : named;
                if (production == null || handled.contains(production)) {
                    continue;
                }
                addition(production, plan.get()).ifPresent(addition -> byIndex.merge(production, addition,
                        FieldAddition::with));
            }
        }
        return List.copyOf(byIndex.values());
    }

    /// Adds the fields promotion found missing ([#promotionAdditions]), once the user approved the promotion.
    ///
    /// @param additions The additions.
    /// @return What was done, said.
    List<String> apply(final List<FieldAddition> additions) {
        final List<String> done = new ArrayList<>();
        for (final FieldAddition addition : additions) {
            final DocRef index = new DocRef(LuceneIndexDoc.TYPE, addition.indexUuid(), addition.indexName());
            final List<String> added = new ArrayList<>();
            addition.plans().forEach(plan -> added.addAll(JsonValues.strings(
                    indexFields.addPromotedLuceneFields(index, plan).get("added"))));
            done.add("index doc " + addition.indexName() + " gained fields " + String.join(", ", added));
        }
        return done;
    }

    // The plan's fields the production index lacks, if any.
    private Optional<FieldAddition> addition(final String indexUuid, final FieldPlan plan) {
        final Optional<LuceneIndexDoc> doc = documentAccess.read(new DocRef(LuceneIndexDoc.TYPE, indexUuid),
                LuceneIndexDoc.class);
        if (doc.isEmpty()) {
            return Optional.empty();
        }
        final Set<String> have = new HashSet<>();
        indexFields.indexFields(new DocRef(LuceneIndexDoc.TYPE, indexUuid, doc.get().getName()))
                .forEach(field -> have.add(String.valueOf(field.get("name"))));
        final List<String> missing = plan.luceneFields().stream()
                .map(field -> String.valueOf(field.get("fldName")))
                .filter(name -> !have.contains(name))
                .toList();
        return missing.isEmpty()
                ? Optional.empty()
                : Optional.of(new FieldAddition(indexUuid, doc.get().getName(), missing, List.of(plan)));
    }

    // The production indexes whose test indexes of the build the pipeline's IndexingFilters name already.
    private List<String> testIndexesNamed(final String build, final MergedPipeline merged) {
        final Map<String, String> tests = testIndexes(build);
        final List<String> out = new ArrayList<>();
        for (final Map.Entry<String, String> element : merged.elements().entrySet()) {
            if (!LUCENE_FILTER.equals(element.getValue())) {
                continue;
            }
            final String uuid = docUuid(merged.properties().get(new ElementProperty(element.getKey(), INDEX)));
            if (uuid != null && tests.containsKey(uuid) && !out.contains(tests.get(uuid))) {
                out.add(tests.get(uuid));
            }
        }
        return out;
    }

    /// The production Lucene indexes a pipeline's IndexingFilters name, by element.
    ///
    /// @param build  The build.
    /// @param merged The pipeline, merged.
    /// @return Each IndexingFilter's id with the UUID of the Lucene index doc it names, where that is outside the
    /// build (and not one of its test indexes).
    Map<String, String> productionIndexes(final String build, final MergedPipeline merged) {
        final Map<String, String> out = new LinkedHashMap<>();
        for (final Map.Entry<String, String> element : merged.elements().entrySet()) {
            if (!LUCENE_FILTER.equals(element.getValue())) {
                continue;
            }
            final String uuid = docUuid(merged.properties().get(new ElementProperty(element.getKey(), INDEX)));
            if (uuid != null && !inBuild(build, new DocRef(LuceneIndexDoc.TYPE, uuid))) {
                out.put(element.getKey(), uuid);
            }
        }
        return out;
    }

    // The UUID of a document a merged property names, as MergedPipeline gives it ({type, uuid, name}).
    private static String docUuid(final Object value) {
        if (value instanceof final Map<?, ?> doc && doc.get("uuid") != null) {
            return String.valueOf(doc.get("uuid"));
        }
        return null;
    }

    private boolean inBuild(final String build, final DocRef ref) {
        return guard.buildOf(ref).filter(build::equals).isPresent();
    }

    // The index plan kept with the pipeline's indexing XSLT, if it keeps one.
    private Optional<FieldPlan> plan(final String pipelineUuid) {
        try {
            return keptMappings.keptMapping(pipelineUuid)
                    .filter(kept -> INDEX.equals(kept.kind()))
                    .map(KeptMapping.Kept::payload)
                    .map(FieldPlan::fromJson)
                    .filter(plan -> FieldPlan.LUCENE.equals(plan.backend()));
        } catch (final RuntimeException e) {
            // Advice: the test index then has the production index's fields alone.
            LOGGER.debug(() -> LogUtil.message("No index plan read for pipeline {}: {}", pipelineUuid,
                    e.getMessage()));
            return Optional.empty();
        }
    }

    // The build's test index of a production Lucene index: the one recorded, else made now as its mirror. Either way
    // it gains the plan's fields it lacks.
    private TestIndex testIndex(final String build, final String productionUuid, final FieldPlan plan) {
        final LuceneIndexDoc production = documentAccess.read(new DocRef(LuceneIndexDoc.TYPE, productionUuid),
                        LuceneIndexDoc.class)
                .orElseThrow(() -> new ToolRefusal("Index " + productionUuid + " can't be read: it doesn't exist, "
                                                   + "or you don't have permission to view it"));
        final DocRef productionRef = new DocRef(LuceneIndexDoc.TYPE, production.getUuid(), production.getName());
        final Optional<DocRef> existing = testIndexOf(build, productionUuid);
        final DocRef test;
        final boolean made = existing.isEmpty();
        if (existing.isPresent()) {
            test = existing.get();
        } else {
            final DocRef created = guard.create(LuceneIndexDoc.TYPE, freeName(build, production.getName()
                                                                                     + TEST_INDEX), build, null,
                    false, IndexExamples.ROLE);
            records.update(build, created, entry -> entry.put(TEST_INDEX_FOR, productionUuid));
            final LuceneIndexDoc doc = documentAccess.read(created, LuceneIndexDoc.class)
                    .orElseThrow(() -> new ToolRefusal("The new index doc " + created.getUuid() + " can't be read"));
            write(doc.copy()
                    .description("The build's test index, standing in for '" + production.getName() + "' while "
                                 + "the build is open: it holds only the sample's documents.")
                    .volumeGroupName(production.getVolumeGroupName())
                    .timeField(production.getTimeField())
                    .partitionBy(production.getPartitionBy())
                    .partitionSize(production.getPartitionSize())
                    .shardsPerPartition(production.getShardsPerPartition())
                    .maxDocsPerShard(production.getMaxDocsPerShard())
                    .defaultExtractionPipeline(production.getDefaultExtractionPipeline())
                    .build());
            indexFields.copyLuceneFields(productionRef, created);
            test = created;
        }
        final List<String> added = plan == null
                ? List.of()
                : JsonValues.strings(indexFields.addPromotedLuceneFields(test, plan).get("added"));
        return new TestIndex(test.getName(), test.getUuid(), made, production.getName(), production.getUuid(),
                added);
    }

    // The name, or the first of `name-2`, `name-3`, ... the build has no index doc of.
    private String freeName(final String build, final String name) {
        final Set<String> taken = new HashSet<>();
        guard.folderContents(build).stream()
                .filter(doc -> LuceneIndexDoc.TYPE.equals(doc.type()))
                .forEach(doc -> taken.add(doc.name()));
        if (!taken.contains(name)) {
            return name;
        }
        for (int n = 2; n <= LAST_FREE_NAME; n++) {
            if (!taken.contains(name + "-" + n)) {
                return name + "-" + n;
            }
        }
        throw new ToolRefusal("Index docs " + name + " to " + name + "-" + LAST_FREE_NAME + " are all in the build");
    }

    @SuppressWarnings("unchecked")
    private void write(final LuceneIndexDoc doc) {
        final DocumentActionHandler<AbstractDoc> handler = documentAccess.handler(LuceneIndexDoc.TYPE)
                .orElseThrow(() -> new ToolRefusal("Stroom has no " + LuceneIndexDoc.TYPE + " store"));
        handler.writeDocument(doc);
    }


    // --------------------------------------------------------------------------------


    /// Fields a production Lucene index gains at promotion, from the plans of the build's pipelines that write to
    /// it.
    ///
    /// @param indexUuid The index doc's UUID.
    /// @param indexName Its name.
    /// @param fields    The fields it lacks.
    /// @param plans     The plans they come from.
    record FieldAddition(String indexUuid, String indexName, List<String> fields, List<FieldPlan> plans) {

        /// @param other Another addition to the same index.
        /// @return Both together.
        FieldAddition with(final FieldAddition other) {
            final List<String> joined = new ArrayList<>(fields);
            other.fields().stream().filter(field -> !joined.contains(field)).forEach(joined::add);
            final List<FieldPlan> both = new ArrayList<>(plans);
            both.addAll(other.plans());
            return new FieldAddition(indexUuid, indexName, joined, both);
        }

        /// @return The change, said for the approval.
        String said() {
            return "index " + indexName + ": adds " + String.join(", ", fields) + " (forward only: documents "
                   + "indexed before don't have them)";
        }
    }

    /// The build's test index a pipeline writes to, in a production index's place.
    ///
    /// @param name           Its name.
    /// @param uuid           Its UUID.
    /// @param made           True if it was made now.
    /// @param productionName The production index doc's name.
    /// @param productionUuid The production index doc's UUID.
    /// @param fieldsAdded    The plan's fields it gained now.
    record TestIndex(String name,
                     String uuid,
                     boolean made,
                     String productionName,
                     String productionUuid,
                     List<String> fieldsAdded) {

        /// @return It as a result gives it.
        Map<String, Object> toMap() {
            final Map<String, Object> map = new LinkedHashMap<>();
            map.put("name", name);
            map.put("uuid", uuid);
            map.put("made", made);
            map.put("stands_in_for", Map.of("name", productionName, "uuid", productionUuid));
            if (!fieldsAdded.isEmpty()) {
                map.put("fields_added", fieldsAdded);
            }
            return map;
        }

        /// @return What a result says of it.
        String said() {
            return "The pipeline indexes into the build's test index " + name + " (" + (made
                    ? "made now"
                    : "the build's") + "), a mirror of production index " + productionName + " with the build's "
                   + "new fields: the production index is never written to by the build. verify_index with "
                   + "index_uuid=" + productionUuid + " searches the test index. Promotion adds the new fields to "
                   + productionName + " and tidies the test index up; indexing production Events into "
                   + productionName + " is then the user's.";
        }
    }
}
