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

import stroom.ai.impl.tools.Globs;
import stroom.ai.impl.tools.JsonValues;
import stroom.ai.impl.tools.ToolText;
import stroom.ai.impl.tools.documents.DocumentAccess;
import stroom.ai.impl.tools.documents.KeptMapping;
import stroom.ai.impl.tools.indexing.IndexNamePatterns;
import stroom.ai.impl.tools.indexing.IndexTemplates;
import stroom.ai.impl.tools.indexing.IndexTemplates.NamedTemplate;
import stroom.docref.DocRef;
import stroom.pipeline.shared.PipelineDoc;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/// Other agreed index templates whose index_patterns also cover an index name: Elasticsearch refuses a new template
/// that overlaps another at the same priority, or applies only one of them. E.g. an old version's `acme-events*`
/// covers a new version's `acme-events-v2`. Read from the agreements kept in the descriptions of the pipelines the
/// user can view ([IndexTemplateAgreements]); only warned of, never blocking: the cluster's own templates aren't
/// read here.
class TemplateOverlaps {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TemplateOverlaps.class);

    // The most pipelines read for their agreements.
    private static final int MAX_PIPELINES = 2_000;

    private final PipelineIndex pipelineIndex;
    private final DocumentAccess documentAccess;

    /// @param pipelineIndex  Lists the pipelines the user can view.
    /// @param documentAccess Reads their descriptions, as the user.
    @Inject
    TemplateOverlaps(final PipelineIndex pipelineIndex, final DocumentAccess documentAccess) {
        this.pipelineIndex = pipelineIndex;
        this.documentAccess = documentAccess;
    }

    /// @param pipelineUuid The pipeline the template is for, whose own agreement is left out.
    /// @param indexName    Its index name, perhaps with variables.
    /// @param cluster      Its cluster's name, or null.
    /// @param templateName The template's name: another pipeline's agreement of the same name is replaced by it.
    /// @param body         The template's body (its priority is read).
    /// @return A warning for each other agreed template covering the index at the same priority.
    List<String> warnings(final String pipelineUuid,
                          final String indexName,
                          final String cluster,
                          final String templateName,
                          final Map<String, Object> body) {
        final List<String> warnings = new ArrayList<>();
        final List<DocRef> pipelines = pipelineIndex.list(PipelineDoc.TYPE);
        for (final DocRef ref : pipelines.subList(0, Math.min(MAX_PIPELINES, pipelines.size()))) {
            if (ref.getUuid().equals(pipelineUuid)) {
                continue;
            }
            try {
                documentAccess.read(ref, PipelineDoc.class)
                        .flatMap(doc -> KeptMapping.readAgreedTemplate(doc.getDescription())
                                .flatMap(agreed -> overlap(NullSafe.nonBlankStringElse(doc.getName(),
                                        ref.getName()), agreed, indexName, cluster, templateName, body)))
                        .ifPresent(warnings::add);
            } catch (final RuntimeException e) {
                // One pipeline gone or unreadable: the others are still read.
                LOGGER.debug(() -> LogUtil.message("Pipeline {} not read: {}", ref.getUuid(), e.getMessage()));
            }
        }
        return warnings;
    }

    /// @param otherPipeline The other pipeline's name.
    /// @param agreed        The template agreed for it, as kept.
    /// @param indexName     This pipeline's index name, perhaps with variables.
    /// @param cluster       This pipeline's cluster, or null.
    /// @param templateName  This template's name.
    /// @param body          This template's body.
    /// @return The warning, when the other template covers the index on the same cluster at the same priority, or
    /// has the same name (committing this one would replace it: for the same index, merged from every feeding
    /// pipeline's plan; for another index, a clash).
    static Optional<String> overlap(final String otherPipeline,
                                    final Map<String, Object> agreed,
                                    final String indexName,
                                    final String cluster,
                                    final String templateName,
                                    final Map<String, Object> body) {
        final String otherName = NullSafe.get(agreed.get("name"), String::valueOf);
        final String otherCluster = NullSafe.get(agreed.get("cluster"), String::valueOf);
        if (cluster != null && otherCluster != null && !cluster.equals(otherCluster)) {
            // Another cluster's.
            return Optional.empty();
        }
        if (Objects.equals(otherName, templateName)) {
            // Agreed for another pipeline: committing this one replaces it. Several pipelines feeding one index
            // share its template, merged from every one's plan; another index's of the same name is a clash.
            final String otherIndex = NullSafe.get(agreed.get("index"), String::valueOf);
            return Optional.of(Objects.equals(otherIndex, indexName)
                    ? "index template '" + otherName + "' is also agreed for pipeline '" + otherPipeline + "', "
                      + "which writes to the same index '" + indexName + "': committing this one replaces it. "
                      + "This template merges the plans of every pipeline writing to the index, so check with the "
                      + "user that it still maps '" + otherPipeline + "''s fields as agreed before it is committed"
                    : "index template '" + otherName + "' is already agreed for pipeline '" + otherPipeline
                      + "' (index '" + otherIndex + "'): committing this one would replace it, and that index "
                      + "would lose its mappings. Give this template another name (template_name=)");
        }
        final NamedTemplate other;
        try {
            other = IndexTemplates.parseTemplate(String.valueOf(agreed.get("dev_tools")));
        } catch (final RuntimeException e) {
            return Optional.empty();
        }
        final Object given = other.body().get("index_patterns");
        final List<String> patterns = given instanceof final String one
                ? List.of(one)
                : JsonValues.strings(given);
        final String looked = IndexNamePatterns.wildcarded(indexName);
        final List<String> covering = patterns.stream()
                .filter(pattern -> Globs.matches(indexName, pattern) || Globs.matches(looked, pattern))
                .toList();
        if (covering.isEmpty() || priority(other.body().get("priority")) != priority(body.get("priority"))) {
            return Optional.empty();
        }
        return Optional.of("index template '" + otherName + "', agreed for pipeline '" + otherPipeline + "' (index '"
                           + agreed.get("index") + "'), has index_patterns " + ToolText.list(patterns) + ", which "
                           + "also cover '" + indexName + "' at the same priority: Elasticsearch refuses a template "
                           + "overlapping another at one priority, or applies only one of them. Ask the user to "
                           + "narrow its patterns (to its exact index name) or to give this template a higher "
                           + "priority");
    }

    // An index template's priority: 0 when not set, as Elasticsearch takes it.
    private static long priority(final Object value) {
        if (value instanceof final Number number) {
            return number.longValue();
        }
        try {
            return value == null
                    ? 0L
                    : Long.parseLong(String.valueOf(value).strip());
        } catch (final NumberFormatException e) {
            return 0L;
        }
    }
}
