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
import stroom.ai.impl.tools.KeptMapping;
import stroom.ai.impl.tools.MergedPipeline;
import stroom.ai.impl.tools.MergedPipeline.TranslationDoc;
import stroom.docref.DocRef;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.XsltDoc;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/// The Elasticsearch index template agreed with the user for an indexing pipeline (the Stroom MCP server's
/// `tools/processing_writes.py` `elastic_destination`, `agreement_problem` and `indexing_xslt_digest`): kept in
/// the pipeline's description, for the index and cluster and the indexing XSLT it was agreed against, so the plan
/// and promotion know whether one is agreed for the documents the pipeline's current code writes.
class IndexTemplateAgreements {

    /// The description block an agreed template is kept in.
    static final String AGREED_TEMPLATE = "agreed index template";

    private final PipelineShapes pipelineShapes;
    private final DocumentAccess documentAccess;

    /// @param pipelineShapes Reads a pipeline as Stroom runs it.
    /// @param documentAccess Reads its XSLTs and its description, as the user.
    @Inject
    IndexTemplateAgreements(final PipelineShapes pipelineShapes, final DocumentAccess documentAccess) {
        this.pipelineShapes = pipelineShapes;
        this.documentAccess = documentAccess;
    }

    /// @param pipelineUuid A pipeline.
    /// @return The cluster and index name an Elasticsearch indexing pipeline writes to (`index name`, `cluster`);
    /// empty for other pipelines.
    /// @throws PipelineUnreadableException if Stroom can't load the pipeline.
    Optional<Map<String, Object>> elasticDestination(final String pipelineUuid) {
        final MergedPipeline merged = pipelineShapes.merged(pipelineUuid);
        final Set<String> elements = merged.elements().entrySet().stream()
                .filter(entry -> "ElasticIndexingFilter".equals(entry.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
        if (elements.isEmpty()) {
            return Optional.empty();
        }
        final Map<String, Object> properties = new LinkedHashMap<>();
        merged.properties().forEach((key, value) -> {
            if (elements.contains(key.element())) {
                properties.put(key.name(), value);
            }
        });
        final Map<String, Object> destination = new LinkedHashMap<>();
        destination.put("index name", properties.get("indexName") == null
                ? null
                : String.valueOf(properties.get("indexName")));
        destination.put("cluster", properties.get("cluster") instanceof final Map<?, ?> cluster
                ? NullSafe.get(cluster.get("name"), String::valueOf)
                : null);
        return Optional.of(destination);
    }

    /// @param pipeline    An Elasticsearch indexing pipeline.
    /// @param destination Where it writes, from [#elasticDestination].
    /// @return Why it has no index template agreed with the user for the documents its current code writes, or
    /// empty when it has one.
    Optional<String> agreementProblem(final PipelineDoc pipeline, final Map<String, Object> destination) {
        final Object index = destination.get("index name");
        final Object cluster = destination.get("cluster");
        if (index == null || "".equals(index)) {
            return Optional.of("This Elasticsearch indexing pipeline has no indexName set");
        }
        final Map<String, Object> agreed = KeptMapping.readBlock(pipeline.getDescription(), AGREED_TEMPLATE)
                .orElse(null);
        if (agreed == null || !Objects.equals(agreed.get("index"), index)
            || !Objects.equals(agreed.get("cluster"), cluster)) {
            return Optional.of("No Elasticsearch index template has been agreed with the user for index '" + index
                               + "' (cluster " + cluster + "). Agreeing it is a step of its own, before any "
                               + "indexing: propose_index_template pipeline_uuid=" + pipeline.getUuid() + " with "
                               + "the field plan, the Events streams and example_template= the example index "
                               + "template (or index mapping) the user gave, exactly as they pasted it, with any "
                               + "component templates it is composed of; the user confirms the result in a form. If "
                               + "you showed them a template for review (needs_review) and they've replied, that is "
                               + "the same call with reviewed=<its review_id>, nothing left out: the form is still "
                               + "to come. Without an example, ask the user for one first. If the user says "
                               + "they've committed a template of their own and want to go on without agreeing one "
                               + "here, call again with template_committed_by_user=true: they confirm that in a "
                               + "form (your word for it isn't theirs). Until a template is agreed (or so "
                               + "confirmed), committed and the sample indexed and verified, the build is not "
                               + "ready for promotion.");
        }
        if (!Objects.equals(agreed.get("xslt"), indexingXsltDigest(pipeline.getUuid()))) {
            final String devTools = NullSafe.string(NullSafe.get(agreed.get("dev_tools"), String::valueOf));
            return Optional.of("The indexing XSLT changed since index template '" + agreed.get("name") + "' was "
                               + "agreed: check_index_template with it (and its component templates "
                               + pythonList(agreed.get("component_templates")) + ") over the Events streams, so "
                               + "the user confirms it again for the documents the pipeline now writes (or, if the "
                               + "user says the template on the cluster is theirs and fits, call again with "
                               + "template_committed_by_user=true, which they confirm in a form). The agreed "
                               + "template:\n" + devTools.substring(0, Math.min(4000, devTools.length())));
        }
        return Optional.empty();
    }

    /// @param pipelineUuid An indexing pipeline.
    /// @return The XSLT code it runs, digested: an index template is agreed for the documents this code writes.
    /// @throws PipelineUnreadableException if Stroom can't load the pipeline.
    String indexingXsltDigest(final String pipelineUuid) {
        final List<String> parts = new ArrayList<>();
        for (final TranslationDoc entry : pipelineShapes.merged(pipelineUuid).translationDocs(pipelineUuid)) {
            if (XsltDoc.TYPE.equals(entry.doc().getType())) {
                parts.add(XsltCode.normalise(documentAccess.read(entry.doc(), XsltDoc.class)
                        .map(XsltDoc::getData)
                        .orElse("")));
            }
        }
        return XsltCode.digest(parts.toArray(String[]::new));
    }

    /// @param pipelineUuid A pipeline.
    /// @return It, if the user can read it.
    Optional<PipelineDoc> pipeline(final String pipelineUuid) {
        return documentAccess.read(new DocRef(PipelineDoc.TYPE, pipelineUuid), PipelineDoc.class);
    }

    // A list as Python writes it in text: ['a', 'b'].
    private static String pythonList(final Object value) {
        final List<String> items = BuildRecords.stringList(value);
        return items.stream()
                .map(item -> "'" + item + "'")
                .collect(Collectors.joining(", ", "[", "]"));
    }
}
