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
import stroom.ai.impl.tools.MergedPipeline.ElementProperty;
import stroom.ai.impl.tools.indexing.IndexingHelpers;
import stroom.ai.impl.tools.indexing.IndexingHelpers.Destination;
import stroom.pipeline.shared.PipelineDoc;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/// The indexes a build writes to (the Stroom MCP server's `tools/indexing.py` `_build_index_docs`,
/// `_build_destinations` and the reads of `other_index`), for the pipeline write tools and the indexing tools: its
/// Elastic Index docs and the index each names, and its Elasticsearch indexing pipelines and the index each writes
/// to.
class BuildIndexes {

    private final WriteGuard guard;
    private final DocumentAccess documentAccess;
    private final PipelineShapes pipelineShapes;

    /// @param guard          Lists the build's documents.
    /// @param documentAccess Reads its Elastic Index docs, as the user.
    /// @param pipelineShapes Reads its pipelines as Stroom runs them.
    @Inject
    BuildIndexes(final WriteGuard guard, final DocumentAccess documentAccess, final PipelineShapes pipelineShapes) {
        this.guard = guard;
        this.documentAccess = documentAccess;
        this.pipelineShapes = pipelineShapes;
    }

    /// @param build A build's name.
    /// @return Its Elastic Index docs' names, and the index name each names (those naming one).
    Map<String, String> indexDocs(final String build) {
        final Map<String, String> named = new LinkedHashMap<>();
        for (final WriteGuard.BuildDoc doc : guard.folderContents(build)) {
            if (ElasticIndexDoc.TYPE.equals(doc.type())) {
                // One gone since the build was listed names nothing.
                documentAccess.read(doc.docRef(), ElasticIndexDoc.class)
                        .map(ElasticIndexDoc::getIndexName)
                        .filter(NullSafe::isNonBlankString)
                        .ifPresent(index -> named.put(doc.name(), index));
            }
        }
        return named;
    }

    /// @param build A build's name.
    /// @return Its Elasticsearch indexing pipelines that have an index name: each's name, UUID, index name and
    /// element.
    List<Destination> destinations(final String build) {
        final List<Destination> found = new ArrayList<>();
        for (final WriteGuard.BuildDoc doc : guard.folderContents(build)) {
            if (!PipelineDoc.TYPE.equals(doc.type())) {
                continue;
            }
            final MergedPipeline merged;
            try {
                merged = pipelineShapes.merged(doc.uuid());
            } catch (final PipelineUnreadableException e) {
                // One Stroom can't load: it writes nowhere yet.
                continue;
            }
            final Optional<String> element = merged.elements().entrySet().stream()
                    .filter(entry -> "ElasticIndexingFilter".equals(entry.getValue()))
                    .map(Map.Entry::getKey)
                    .findFirst();
            if (element.isPresent()
                && merged.properties().get(new ElementProperty(element.get(), "indexName")) instanceof
                           final String written
                && !written.isEmpty()) {
                found.add(new Destination(doc.name(), doc.uuid(), written, element.get()));
            }
        }
        return found;
    }

    /// Why a pipeline's index name isn't the one meant ([IndexingHelpers#otherIndex]): the build has an Elastic
    /// Index doc no other indexing pipeline of the build writes to, and this name writes to no doc.
    ///
    /// @param build        The pipeline's build, or null.
    /// @param written      The pipeline's indexName.
    /// @param pipelineUuid The pipeline's UUID, or null.
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
}
