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

import stroom.ai.impl.tools.DocumentAccess;
import stroom.ai.impl.tools.MergedPipeline;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.ToolSession;
import stroom.ai.impl.tools.indexing.IndexingHelpers;
import stroom.ai.impl.tools.indexing.IndexingHelpers.Destination;
import stroom.docref.DocRef;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.data.PipelinePropertyValue;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/// The index names a build uses (the Stroom MCP server's `tools/indexing.py` `_build_destinations`,
/// `_build_index_docs`, `other_index`, `_literal_names`, `_set_index_name`, `_the_cluster_in_use` and `_same_index`):
/// the build's Elastic Index docs and the Elasticsearch indexing pipelines writing to them, which name one index
/// between them. create_indexing_pipeline's and create_pipeline's ports check an index name with [#otherIndex].
class BuildIndexes {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(BuildIndexes.class);

    // How many Elastic Index docs are read to find the cluster they use.
    private static final int CLUSTER_LOOKUP = 50;

    private final WriteGuard guard;
    private final PipelineShapes pipelineShapes;
    private final DocumentAccess documentAccess;
    private final PipelineDocs pipelineDocs;
    private final PipelineIndex pipelineIndex;
    private final BuildPlan plan;

    /// @param guard          Lists a build's documents, and checks a pipeline is the build's.
    /// @param pipelineShapes Reads a pipeline as Stroom runs it.
    /// @param documentAccess Reads Elastic Index docs, as the user.
    /// @param pipelineDocs   Saves a pipeline's index name.
    /// @param pipelineIndex  Lists the Elastic Index docs.
    /// @param plan           Says which build a pipeline is in.
    @Inject
    BuildIndexes(final WriteGuard guard,
                 final PipelineShapes pipelineShapes,
                 final DocumentAccess documentAccess,
                 final PipelineDocs pipelineDocs,
                 final PipelineIndex pipelineIndex,
                 final BuildPlan plan) {
        this.guard = guard;
        this.pipelineShapes = pipelineShapes;
        this.documentAccess = documentAccess;
        this.pipelineDocs = pipelineDocs;
        this.pipelineIndex = pipelineIndex;
        this.plan = plan;
    }

    /// @param build A build.
    /// @return The build's Elasticsearch indexing pipelines with an index name: name, uuid, index name, element.
    List<Destination> destinations(final String build) {
        final List<Destination> out = new ArrayList<>();
        for (final WriteGuard.BuildDoc doc : guard.folderContents(build)) {
            if (!PipelineDoc.TYPE.equals(doc.type())) {
                continue;
            }
            final MergedPipeline merged;
            try {
                merged = pipelineShapes.merged(doc.uuid());
            } catch (final RuntimeException e) {
                // One Stroom can't load: it writes nowhere yet.
                continue;
            }
            destinationOf(doc.name(), doc.uuid(), merged).ifPresent(out::add);
        }
        return out;
    }

    /// @param name   A pipeline's name.
    /// @param uuid   Its UUID.
    /// @param merged It as Stroom runs it.
    /// @return Where it writes, if it is an Elasticsearch indexing pipeline with an index name.
    static Optional<Destination> destinationOf(final String name, final String uuid, final MergedPipeline merged) {
        final String element = merged.elements().entrySet().stream()
                .filter(entry -> "ElasticIndexingFilter".equals(entry.getValue()))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
        if (element == null) {
            return Optional.empty();
        }
        final Object written = merged.properties().get(new MergedPipeline.ElementProperty(element, "indexName"));
        return written instanceof final String text && !text.isEmpty()
                ? Optional.of(new Destination(name, uuid, text, element))
                : Optional.empty();
    }

    /// @param build A build.
    /// @return The build's Elastic Index docs and the index name each names (those naming none left out).
    Map<String, String> indexDocs(final String build) {
        final Map<String, String> named = new LinkedHashMap<>();
        for (final WriteGuard.BuildDoc doc : guard.folderContents(build)) {
            if (ElasticIndexDoc.TYPE.equals(doc.type())) {
                // Gone since the build was listed: left out.
                documentAccess.read(new DocRef(ElasticIndexDoc.TYPE, doc.uuid(), doc.name()), ElasticIndexDoc.class)
                        .map(ElasticIndexDoc::getIndexName)
                        .filter(NullSafe::isNonEmptyString)
                        .ifPresent(indexName -> named.put(doc.name(), indexName));
            }
        }
        return named;
    }

    /// Why a pipeline's index name isn't the one meant: the build has an Elastic Index doc no other indexing
    /// pipeline of the build writes to, and this name writes to no doc. Empty otherwise: a build may index into
    /// several indices, and an index's doc may come after its pipeline (a discovery index, a second index for
    /// comparison).
    ///
    /// @param build        The build, or null (nothing to compare).
    /// @param written      The pipeline's index name.
    /// @param pipelineUuid The pipeline, or null.
    /// @return Why, or empty.
    Optional<String> otherIndex(final String build, final String written, final String pipelineUuid) {
        if (NullSafe.isBlankString(build)) {
            return Optional.empty();
        }
        final Map<String, String> docs = indexDocs(build);
        if (docs.isEmpty() || docs.values().stream().anyMatch(name -> IndexingHelpers.writesToIndex(written, name))) {
            return Optional.empty();
        }
        return Optional.ofNullable(IndexingHelpers.otherIndex(written, docs, destinations(build), pipelineUuid));
    }

    /// Refuses a template for an index other than the one the build's Elastic Index doc names.
    ///
    /// @param pipelineUuid The indexing pipeline.
    /// @param index        The index name it writes to.
    /// @throws ToolRefusal if the build's doc names another.
    void requireSameIndex(final String pipelineUuid, final String index) {
        final String build = plan.buildOf(new DocRef(PipelineDoc.TYPE, pipelineUuid)).orElse(null);
        otherIndex(build, index, pipelineUuid).ifPresent(other -> {
            throw new ToolRefusal(other + " Ask the user which name is meant; rename_index sets it in both (the "
                                  + "pipeline and the doc), then propose the template again.");
        });
    }

    /// Refuses renaming a pipeline whose index name is built from values: the new name would lose them.
    ///
    /// @param pipelines The pipelines to rename.
    /// @throws ToolRefusal if any builds its index name from values.
    static void refuseBuiltNames(final List<Destination> pipelines) {
        for (final Destination pipeline : pipelines) {
            if (pipeline.indexName().contains("{")) {
                throw new ToolRefusal("Pipeline '" + pipeline.name() + "' builds its index name from values ('"
                                      + pipeline.indexName() + "'): another name would lose them. Ask the user for "
                                      + "the name with the same values, and set it on the pipeline (update_pipeline, "
                                      + "its " + pipeline.element() + ".indexName) after the doc.");
            }
        }
    }

    /// Sets the index name a build's pipeline writes to (its own property, over its templates').
    ///
    /// @param session   The session, for the audit of a refusal.
    /// @param pipeline  The pipeline.
    /// @param indexName The index name.
    /// @throws ToolRefusal if the pipeline isn't the build's own.
    void setIndexName(final ToolSession session, final Destination pipeline, final String indexName) {
        final PipelineDoc doc = pipelineDocs.read(pipeline.uuid());
        guard.checkInBuild(session, new DocRef(PipelineDoc.TYPE, doc.getUuid(), doc.getName()));
        pipelineDocs.write(PipelineDocs.withProperty(doc, pipeline.element(), "indexName",
                new PipelinePropertyValue(indexName)));
    }

    /// @return The one Elastic Cluster doc the environment's Elastic Index docs use; empty when they use several,
    /// or there are none.
    Optional<DocRef> clusterInUse() {
        final Set<String> used = new HashSet<>();
        DocRef cluster = null;
        try {
            final List<DocRef> refs = pipelineIndex.list(ElasticIndexDoc.TYPE);
            for (final DocRef ref : refs.subList(0, Math.min(CLUSTER_LOOKUP, refs.size()))) {
                // Listed, but gone: passed over.
                final DocRef clusterRef = documentAccess.read(ref, ElasticIndexDoc.class)
                        .map(ElasticIndexDoc::getClusterRef)
                        .orElse(null);
                if (clusterRef != null && NullSafe.isNonBlankString(clusterRef.getUuid())) {
                    used.add(clusterRef.getUuid());
                    cluster = clusterRef;
                }
            }
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> "Unable to list the clusters in use: " + e.getMessage());
            return Optional.empty();
        }
        return used.size() == 1
                ? Optional.of(cluster)
                : Optional.empty();
    }
}
