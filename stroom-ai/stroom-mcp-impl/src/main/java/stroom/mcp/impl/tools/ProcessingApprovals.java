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
import stroom.ai.impl.tools.KeptMapping;
import stroom.ai.impl.tools.StroomLinks;
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.docref.DocRef;
import stroom.index.shared.LuceneIndexDoc;
import stroom.pipeline.shared.PipelineDoc;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/// What `create_processor_filter` and `reprocess_streams` share between them: whether processing runs on the user's
/// approval, the Elasticsearch index template the user agreed (or confirmed as their own), and what the user does
/// before a filter is enabled or an index is written to again, as text and as buttons.
class ProcessingApprovals {

    /// The pipelines whose processing in a build runs on the user's approval (asked for by the user): events (or
    /// records) and indexing. Every other filter is created disabled, for the user to enable.
    static final Set<String> REPROCESS_STAGES = Set.of("translation", "records", "indexing", "discovery");
    /// The details key for the index template committed to the cluster.
    static final String INDEX_TEMPLATE = "index template";
    // What comes first, before a filter is enabled.
    private static final List<String> FIRST = List.of(INDEX_TEMPLATE, IndexingRuns.ALREADY_INDEXED);
    private static final String COMMIT_TEMPLATE = "commit-template";
    private static final DateTimeFormatter AGREED_AT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
            .withZone(ZoneOffset.UTC);

    private final PipelineShapes pipelineShapes;
    private final PipelineOutputs pipelineOutputs;
    private final IndexTemplateAgreements agreements;
    private final PipelineDocs pipelineDocs;
    private final Consent consent;

    /// @param pipelineShapes  Reads a pipeline's stage.
    /// @param pipelineOutputs Says whether a pipeline indexes.
    /// @param agreements      Says whether an index template is agreed for a pipeline's code.
    /// @param pipelineDocs    Keeps the user's own template with the pipeline.
    /// @param consent         Asks the user to confirm their own template.
    @Inject
    ProcessingApprovals(final PipelineShapes pipelineShapes,
                        final PipelineOutputs pipelineOutputs,
                        final IndexTemplateAgreements agreements,
                        final PipelineDocs pipelineDocs,
                        final Consent consent) {
        this.pipelineShapes = pipelineShapes;
        this.pipelineOutputs = pipelineOutputs;
        this.agreements = agreements;
        this.pipelineDocs = pipelineDocs;
        this.consent = consent;
    }

    /// @param pipelineUuid A pipeline.
    /// @return Whether processing it in a build runs on the user's approval: an events (or records) or indexing
    /// pipeline.
    boolean reprocessRuns(final String pipelineUuid) {
        try {
            return REPROCESS_STAGES.contains(pipelineShapes.shape(pipelineUuid).stage());
        } catch (final RuntimeException e) {
            return pipelineOutputs.isIndexing(pipelineUuid);
        }
    }

    /// Elasticsearch: the index template has to be on the cluster before documents arrive, or the index is created with
    /// dynamic mappings that the template can no longer change. Only a template the user agreed (kept with the pipeline
    /// by propose_index_template or check_index_template) is asked about, and the user confirms it is committed to the
    /// cluster in the approval: the approval's detail, as text. With ownTemplate, the user says one of their own is
    /// committed: they confirm going on without agreeing one here, in a form before it goes on, and it is kept with the
    /// pipeline in place of an agreed one. Seen in testing: the user had committed their template, and the agreement
    /// still stopped indexing, however they said so.
    ///
    /// @param call           The call, whose id tells its forms apart.
    /// @param context        The call's context.
    /// @param pipeline       The Elasticsearch indexing pipeline.
    /// @param destination    Where it writes ([IndexTemplateAgreements#elasticDestination]).
    /// @param action         The tool asking.
    /// @param ownTemplate    True if the user says a template of their own is committed.
    /// @return What the approval says of the template, with the pipeline as it now is.
    /// @throws ToolRefusal if no template is agreed for the pipeline's code, and the user said none of their own is
    ///                     committed.
    Committed committed(final ToolCall call,
                        final ToolCallContext context,
                        final PipelineDoc pipeline,
                        final Map<String, Object> destination,
                        final String action,
                        final boolean ownTemplate) {
        final Optional<String> problem = agreements.agreementProblem(pipeline, destination);
        if (problem.isPresent() && !ownTemplate) {
            throw new ToolRefusal(problem.get());
        }
        final Object index = destination.get("index name");
        final Object cluster = destination.get("cluster");
        PipelineDoc current = pipeline;
        if (problem.isPresent()) {
            final Map<String, Object> details = new LinkedHashMap<>();
            details.put("index", index + " (cluster " + cluster + ")");
            details.put("you say", "an index template of yours for it is already committed to the cluster");
            details.put("not checked", "that template against the documents the pipeline writes: a field it maps "
                                       + "otherwise is rejected or mis-mapped");
            consent.require(call, context, Consent.Request.confirmation(
                    action, "Index into Elasticsearch index '" + index + "' with your own index template, not one "
                            + "agreed here", details));
            final PipelineDoc doc = pipelineDocs.read(pipeline.getUuid());
            final Map<String, Object> agreed = new LinkedHashMap<>();
            agreed.put("name", "the user's own");
            agreed.put("index", index);
            agreed.put("cluster", cluster);
            agreed.put("component_templates", List.of());
            agreed.put("xslt", agreements.indexingXsltDigest(pipeline.getUuid()));
            agreed.put("agreed", AGREED_AT.format(Instant.now().truncatedTo(ChronoUnit.SECONDS)));
            agreed.put("dev_tools", "");
            agreed.put("by_user", true);
            current = pipelineDocs.write(doc.copy()
                    .description(KeptMapping.withAgreedTemplate(doc.getDescription(), agreed))
                    .build());
        }
        final Map<String, Object> agreed = KeptMapping.readAgreedTemplate(current.getDescription()).orElse(Map.of());
        if (JsonValues.truthy(agreed.get("by_user"))) {
            return new Committed("the user's own index template for Elasticsearch index '" + index + "' is "
                                 + "committed to cluster " + cluster + " (confirmed by the user, not checked here)",
                    current);
        }
        return new Committed("the agreed index template '" + agreed.get("name") + "' for Elasticsearch index '"
                             + index + "' is committed to cluster " + cluster, current);
    }

    /// What the user needs to enable a disabled filter: where, what it will do, and what must be done first; with
    /// user_actions, the same as buttons (before: the actions that come first).
    ///
    /// @param links    How the caller links to Stroom.
    /// @param pipeline The pipeline.
    /// @param filterId The disabled filter.
    /// @param details  What the filter does, as the approval would have said it.
    /// @param before   The buttons for what comes first, any of them null.
    /// @return The result's entries for the user.
    static Map<String, Object> forTheUser(final StroomLinks links,
                                          final DocRef pipeline,
                                          final int filterId,
                                          final Map<String, Object> details,
                                          final List<Map<String, Object>> before) {
        final List<String> first = new ArrayList<>();
        for (final String key : FIRST) {
            if (details.containsKey(key)) {
                first.add(INDEX_TEMPLATE.equals(key)
                        ? details.get(key) + ": enable it only once that is so"
                        : String.valueOf(details.get(key)));
            }
        }
        final List<Map<String, Object>> kept = before.stream().filter(Objects::nonNull).toList();
        final Map<String, Object> enable = UserActionEntries.enableFilter(links, filterId, pipeline,
                NullSafe.string(NullSafe.get(details.get("scope"), String::valueOf)),
                kept.stream().map(entry -> String.valueOf(entry.get("id"))).toList());
        final Map<String, Object> enablingIt = new LinkedHashMap<>();
        details.forEach((key, value) -> {
            if (!FIRST.contains(key)) {
                enablingIt.put(key, value);
            }
        });
        final Map<String, Object> out = new LinkedHashMap<>();
        out.put("pipeline_link", links.documentTarget(pipeline));
        out.put("enabling_it", enablingIt);
        if (!first.isEmpty()) {
            out.put("before_enabling", first);
        }
        final List<Map<String, Object>> actions = new ArrayList<>(kept);
        actions.add(enable);
        UserActionEntries.withEntries(out, actions);
        out.put("hint", "Filter " + filterId + " is disabled: enabling it is the user's. Give them the "
                        + "pipeline_link and ask them to enable filter " + filterId + " on its Processors tab"
                        + (first.isEmpty()
                ? ""
                : ", once what before_enabling says is done")
                        + ", and to tell you when they have. Then wait_for_processing (filter_id=" + filterId
                        + "); not before.");
        return out;
    }

    /// Buttons for what comes before enabling a filter: the agreed index template committed, an index this pipeline
    /// wrote to before dropped (Elasticsearch: copied for the cluster admin), a Lucene index's shards deleted (its doc
    /// opened).
    ///
    /// @param links       How the caller links to Stroom.
    /// @param pipeline    The pipeline, with its agreed template.
    /// @param destination Its Elasticsearch destination, or null.
    /// @param lucene      Its Lucene index, or null.
    /// @param details     What the approval says.
    /// @return The buttons, any of them null (a request not of a shape offered).
    static List<Map<String, Object>> beforeActions(final StroomLinks links,
                                                   final PipelineDoc pipeline,
                                                   final Map<String, Object> destination,
                                                   final DocRef lucene,
                                                   final Map<String, Object> details) {
        final List<Map<String, Object>> out = new ArrayList<>();
        if (destination != null) {
            final Map<String, Object> agreed = KeptMapping.readAgreedTemplate(pipeline.getDescription())
                    .orElse(Map.of());
            final String devTools = NullSafe.get(agreed.get("dev_tools"), String::valueOf);
            if (NullSafe.isNonEmptyString(devTools)) {
                out.add(UserActionEntries.esRequest(COMMIT_TEMPLATE, devTools, "Commit the agreed index template '"
                                                                               + agreed.get("name") + "' to cluster "
                                                                               + destination.get("cluster")
                                                                               + ", if it isn't already"));
            }
            if (details.containsKey(IndexingRuns.ALREADY_INDEXED)) {
                final String index = String.valueOf(destination.get("index name"));
                out.add(UserActionEntries.esRequest("drop-index", IndexingRuns.dropRequest(pipeline, index),
                        "Drop '" + index + "' so it starts fresh"));
            }
        } else if (lucene != null && details.containsKey(IndexingRuns.ALREADY_INDEXED)) {
            out.add(UserActionEntries.openDocument(links, withIndexType(lucene), "delete its shards on the Shards "
                                                                                 + "tab, so it starts fresh"));
        }
        return out;
    }

    /// The step before processing into an index this pipeline wrote to: the user drops it, from a button. Asked for by
    /// the user: the drop was only a line in the approval form, which carries no button, and an updated indexing
    /// pipeline went on into the index it had written to, with that index's earlier documents and mapping.
    ///
    /// @param links       How the caller links to Stroom.
    /// @param pipeline    The pipeline.
    /// @param destination Its Elasticsearch destination, or null.
    /// @param lucene      Its Lucene index, or null.
    /// @param details     What the approval says, with what is already indexed.
    /// @param tool        The tool to call again once the index is dropped.
    /// @return The `drop_first` round.
    static Map<String, Object> dropFirst(final StroomLinks links,
                                         final PipelineDoc pipeline,
                                         final Map<String, Object> destination,
                                         final DocRef lucene,
                                         final Map<String, Object> details,
                                         final String tool) {
        final List<Map<String, Object>> drop = beforeActions(links, pipeline, destination, lucene, details).stream()
                .filter(action -> action != null && !COMMIT_TEMPLATE.equals(action.get("id")))
                .toList();
        final Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", "drop_first");
        out.put("pipeline", pipeline.getName());
        out.put("already_indexed", details.get(IndexingRuns.ALREADY_INDEXED));
        UserActionEntries.withEntries(out, drop);
        out.put("hint", "Nothing has been processed. Before this pipeline indexes again, the index it wrote to must "
                        + "be dropped, so it starts fresh with the agreed template's mapping: an existing index (or "
                        + "data stream) keeps the mapping it was made with. Show the user already_indexed and the "
                        + "button, ask them to have it dropped and to tell you when it is; then " + tool + " again "
                        + "with the same arguments and index_dropped=true, whose approval follows. Don't pass "
                        + "index_dropped before they say so.");
        return out;
    }

    /// @param output The build's test feed the pipeline writes to.
    /// @return What the result says of it.
    static String outputNote(final OutputFeeds.OutputFeed output) {
        return "The streams are processed where they are, in " + String.join(", ", output.worksFrom()) + "; their "
               + "Events go to the build's test feed " + output.name() + (output.made()
                ? " (made now)"
                : "") + ", through the pipeline's StreamAppender (its feed property), not into the production feed. "
               + "Nothing is uploaded to it. Promotion takes the property off and leaves the test feed in the "
               + "workspace.";
    }

    /// @param index A Lucene index.
    /// @return It, typed as an index doc when its type isn't known.
    static DocRef withIndexType(final DocRef index) {
        return new DocRef(NullSafe.nonBlankStringElse(index.getType(), LuceneIndexDoc.TYPE), index.getUuid(),
                index.getName());
    }


    // --------------------------------------------------------------------------------


    /// What the approval says of the index template.
    ///
    /// @param text     The approval's detail, e.g. `the agreed index template 'x' ... is committed to cluster C`.
    /// @param pipeline The pipeline, with the user's own template kept if they confirmed one now.
    record Committed(String text, PipelineDoc pipeline) {

    }
}
