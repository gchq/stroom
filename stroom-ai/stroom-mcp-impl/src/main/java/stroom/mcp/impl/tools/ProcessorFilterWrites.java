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
import stroom.ai.impl.tools.ProcessorFilterAccess;
import stroom.ai.impl.tools.StreamReader;
import stroom.ai.impl.tools.ToolSession;
import stroom.docref.DocRef;
import stroom.feed.shared.FeedDoc;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaFields;
import stroom.meta.shared.MetaRow;
import stroom.meta.shared.Status;
import stroom.pipeline.shared.PipelineDoc;
import stroom.processor.api.ProcessorFilterService;
import stroom.processor.api.ProcessorService;
import stroom.processor.shared.CreateProcessFilterRequest;
import stroom.processor.shared.Processor;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorType;
import stroom.processor.shared.QueryData;
import stroom.query.api.ExpressionItem;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// How the processing write tools make and read processor filters, as the user: a filter on a pipeline in a build,
/// what a filter selects, the streams a pipeline already processed, and the bounds of a feed-wide filter. Ported
/// from the Stroom MCP server's `tools/processing_writes.py` (`_term`, `_managed_pipeline`, `_create_filter`,
/// `_selected_ids`, `_already_processed`, `_feed_term`, `_pipeline_term`, `feed_stream_type`, `filter_feeds`,
/// `_pipeline_terms`, `_names_pipeline`, `_feed_bounds` and `_refuse_duplicate`), for `create_processor_filter`,
/// `reprocess_streams` and promotion (`promote_build`) to share.
///
/// Filters are made through Stroom's [ProcessorFilterService], as the Processors tab and the data browser's
/// Process action make them: the user needs the Manage Processors permission and View on the pipeline.
class ProcessorFilterWrites {

    /// The most streams reprocessed in one call (the Stroom MCP server's `max_reprocess_streams`).
    static final int MAX_REPROCESS_STREAMS = 10;
    /// The task limit of a reprocessing filter (`reprocess_max_tasks`).
    static final int REPROCESS_MAX_TASKS = 1;
    /// The task limit of a filter over a build's sample streams (`sample_max_tasks`).
    static final int SAMPLE_MAX_TASKS = 1;
    /// The task limit of a feed-wide filter (`max_feed_filter_tasks`).
    static final int MAX_FEED_FILTER_TASKS = 2;
    /// The priority a filter is given unless the call says otherwise.
    static final int DEFAULT_PRIORITY = 10;
    /// The most streams of a feed looked at for a feed-wide filter's bounds.
    static final int FEED_STREAMS = 100;

    private static final String RAW_EVENTS = "Raw Events";
    private static final Pattern FEED_TERM = Pattern.compile("^Feed (EQUALS|IS_DOC_REF) (.*)$", Pattern.DOTALL);
    private static final Pattern DIGITS = Pattern.compile("[0-9]+");

    private final ProcessorFilterAccess processorFilterAccess;
    private final Provider<ProcessorFilterService> processorFilterServiceProvider;
    private final Provider<ProcessorService> processorServiceProvider;
    private final StreamProcessing streamProcessing;
    private final StreamReader streamReader;
    private final FeedNames feedNames;
    private final DocumentAccess documentAccess;
    private final PipelineDocs pipelineDocs;
    private final PipelineOutputs pipelineOutputs;
    private final WriteGuard guard;

    /// @param processorFilterAccess          Lists a pipeline's filters as the user.
    /// @param processorFilterServiceProvider Makes filters as the user.
    /// @param processorServiceProvider       Enables a pipeline's processor as the user.
    /// @param streamProcessing               Reads what a pipeline produced from a stream.
    /// @param streamReader                   Finds streams as the user.
    /// @param feedNames                      Finds feeds by name.
    /// @param documentAccess                 Reads feeds as the user.
    /// @param pipelineDocs                   Reads pipelines as the user.
    /// @param pipelineOutputs                Says whether a pipeline indexes.
    /// @param guard                          Checks a pipeline is in a build.
    @Inject
    ProcessorFilterWrites(final ProcessorFilterAccess processorFilterAccess,
                          final Provider<ProcessorFilterService> processorFilterServiceProvider,
                          final Provider<ProcessorService> processorServiceProvider,
                          final StreamProcessing streamProcessing,
                          final StreamReader streamReader,
                          final FeedNames feedNames,
                          final DocumentAccess documentAccess,
                          final PipelineDocs pipelineDocs,
                          final PipelineOutputs pipelineOutputs,
                          final WriteGuard guard) {
        this.processorFilterAccess = processorFilterAccess;
        this.processorFilterServiceProvider = processorFilterServiceProvider;
        this.processorServiceProvider = processorServiceProvider;
        this.streamProcessing = streamProcessing;
        this.streamReader = streamReader;
        this.feedNames = feedNames;
        this.documentAccess = documentAccess;
        this.pipelineDocs = pipelineDocs;
        this.pipelineOutputs = pipelineOutputs;
        this.guard = guard;
    }

    /// A pipeline the tools created, in its build (`_managed_pipeline`): processing it may set its batch size,
    /// and keeps its agreed template.
    ///
    /// @param session The session the call is made in, for the audit log.
    /// @param uuid    The pipeline's UUID.
    /// @return The pipeline.
    /// @throws ToolRefusal if it can't be read, or it isn't the tools' own in a build folder.
    PipelineDoc managedPipeline(final ToolSession session, final String uuid) {
        final PipelineDoc doc = pipelineDocs.read(uuid);
        guard.checkInBuild(session, ref(doc));
        return doc;
    }

    /// @param doc A pipeline.
    /// @return Its reference.
    static DocRef ref(final PipelineDoc doc) {
        return new DocRef(PipelineDoc.TYPE, doc.getUuid(), doc.getName());
    }

    /// @param pipeline A pipeline.
    /// @return Its processor filters, as the Processors tab lists them.
    /// @throws ToolRefusal if Stroom won't list them, saying why.
    List<ProcessorFilter> filters(final DocRef pipeline) {
        final ProcessorFilterAccess.Filters found = processorFilterAccess.filtersOf(pipeline);
        if (found.refused() != null) {
            throw new ToolRefusal("Stroom refused to list this pipeline's processor filters: " + found.refused()
                                  + ".");
        }
        return found.filters();
    }

    /// Makes a processor filter on the pipeline (`_create_filter`), selecting from the stream store.
    ///
    /// Stroom gives a pipeline's new processor its first filter's enabled flag, and a disabled processor runs none
    /// of its filters: the user enabling the filter alone saw nothing processed. A processor whose filters are all
    /// disabled runs nothing, so the one this filter made is enabled and enabling stays the filter's, the user's
    /// choice. One already there keeps its state, unless the user approved this run.
    ///
    /// @param pipeline    The pipeline.
    /// @param expression  What the filter selects.
    /// @param priority    Its priority.
    /// @param maxTasks    Its task limit (0 for none).
    /// @param minCreateMs Only streams created after this time, or null for any.
    /// @param enabled     True for a filter that runs at once, false for one the user enables.
    /// @return The filter made.
    /// @throws ToolRefusal if Stroom refuses to make it, saying why.
    ProcessorFilter createFilter(final DocRef pipeline,
                                 final ExpressionOperator expression,
                                 final int priority,
                                 final int maxTasks,
                                 final Long minCreateMs,
                                 final boolean enabled) {
        final boolean hadProcessor = !filters(pipeline).isEmpty();
        final CreateProcessFilterRequest request = CreateProcessFilterRequest.builder()
                .processorType(ProcessorType.PIPELINE)
                .pipeline(pipeline)
                .queryData(QueryData.builder()
                        .dataSource(MetaFields.STREAM_STORE_DOC_REF)
                        .expression(expression)
                        .build())
                .enabled(enabled)
                .priority(priority)
                .autoPriority(false)
                .reprocess(false)
                .export(false)
                .maxProcessingTasks(maxTasks)
                .minMetaCreateTimeMs(minCreateMs)
                .build();
        final ProcessorFilter created;
        try {
            created = processorFilterServiceProvider.get().create(request);
        } catch (final RuntimeException e) {
            throw new ToolRefusal("Stroom refused to create the processor filter: " + PipelineShapes.reason(e));
        }
        final Processor processor = created.getProcessor();
        if (processor != null && processor.getId() != null && !processor.isEnabled()
            && (enabled || !hadProcessor)) {
            processorServiceProvider.get().setEnabled(processor.getId(), true);
        }
        return created;
    }

    /// @param pipeline  A pipeline.
    /// @param streamIds Streams.
    /// @return Those the pipeline has output for, or that one of its filters already selects, in the order given.
    List<Long> alreadyProcessed(final DocRef pipeline, final List<Long> streamIds) {
        final Set<Long> selected = new TreeSet<>();
        for (final ProcessorFilter filter : filters(pipeline)) {
            selected.addAll(selectedIds(expressionOf(filter)));
        }
        return streamIds.stream()
                .filter(id -> selected.contains(id)
                              || !streamProcessing.outputs(id, pipeline.getUuid(), null).isEmpty())
                .toList();
    }

    /// The filter's condition on its feed, by reference (Feed IS_DOC_REF), not by name (`_feed_term`): Stroom then
    /// keeps it as a dependency of the feed, and a renamed feed is still the one selected (asked for by the user).
    ///
    /// @param feed A feed's name.
    /// @return The condition.
    /// @throws ToolRefusal if there is no feed of exactly that name.
    ExpressionTerm feedTerm(final String feed) {
        final DocRef ref = feedNames.exact(feed)
                .filter(found -> NullSafe.isNonBlankString(found.getUuid()))
                .orElseThrow(() -> new ToolRefusal("No feed named '" + feed + "' in Stroom: give the feed's exact "
                                                   + "name"));
        return ExpressionTerm.builder()
                .field(ProcessorFilters.FEED)
                .condition(Condition.IS_DOC_REF)
                .docRef(new DocRef(FeedDoc.TYPE, ref.getUuid(), NullSafe.nonBlankStringElse(ref.getName(), feed)))
                .build();
    }

    /// @param feed A feed's name.
    /// @return The stream type it receives (Raw Events, Raw Reference, ...), from the feed itself; Raw Events when
    /// it can't be read.
    String feedStreamType(final String feed) {
        try {
            return feedNames.exact(feed)
                    .flatMap(ref -> documentAccess.read(ref, FeedDoc.class))
                    .map(FeedDoc::getStreamType)
                    .filter(NullSafe::isNonBlankString)
                    .orElse(RAW_EVENTS);
        } catch (final RuntimeException e) {
            return RAW_EVENTS;
        }
    }

    /// A feed-wide filter's lower bound, kept to the feed's own streams (`_feed_bounds`).
    ///
    /// Seen (VS Code), the same day: Gemma guessed created_after at midnight after its sample arrived, so the filter
    /// selected nothing and the agent waited out ten minutes; Qwen chose a date before the feed was made, which also
    /// selects the streams of an earlier, deleted feed of that name. Raised to the feed's creation; a translation
    /// pipeline's filter is refused when the feed has streams of its own that nothing has processed or will, and it
    /// selects none of them. A filter for new data only, once the feed's streams are processed, is left alone.
    ///
    /// @param pipeline   The pipeline the filter is for.
    /// @param feed       The feed.
    /// @param streamType The stream type it selects.
    /// @param minMs      The time the call gave, in milliseconds.
    /// @return The bound, and how the scope says it.
    /// @throws ToolRefusal if it selects none of the feed's streams still to process.
    FeedBounds feedBounds(final DocRef pipeline, final String feed, final String streamType, final long minMs) {
        final Long created = feedCreatedMs(feed).orElse(null);
        long bound = minMs;
        String said = StreamProcessing.iso(minMs);
        if (created != null && created != 0 && minMs < created) {
            bound = created;
            said = StreamProcessing.iso(created) + " (when the feed was created: streams before then belong to an "
                   + "earlier feed of that name)";
        }
        final ExpressionOperator expression = ExpressionOperator.builder().op(Op.AND)
                .addTextTerm(MetaFields.FEED, Condition.EQUALS, feed)
                .addTextTerm(MetaFields.TYPE, Condition.EQUALS, streamType)
                .build();
        final List<Meta> own = NullSafe.list(NullSafe.get(streamReader.find(expression, FEED_STREAMS, true),
                        ResultPage::getValues)).stream()
                .map(MetaRow::getMeta)
                .filter(Objects::nonNull)
                .filter(meta -> meta.getStatus() != Status.DELETED)
                .filter(meta -> created == null || meta.getCreateMs() >= created)
                .filter(meta -> meta.getCreateMs() != 0)
                .toList();
        final long earliest = bound;
        if (own.isEmpty() || own.stream().anyMatch(meta -> meta.getCreateMs() >= earliest)
            || ProcessorFilters.EVENTS.equals(streamType) || pipelineOutputs.isIndexing(pipeline.getUuid())) {
            // A pipeline reading Events (indexing, CEF output) may well take new data only: a new index version,
            // the older Events in the old one; ArcSight from now on.
            return new FeedBounds(bound, said);
        }
        final List<ProcessorFilter> filters = filters(pipeline);
        final Set<Long> handled = Set.copyOf(alreadyProcessed(pipeline, own.stream().map(Meta::getId).toList()));
        final List<Meta> waiting = own.stream()
                .filter(meta -> !handled.contains(meta.getId())
                                && filters.stream().noneMatch(filter -> StreamProcessing.willProcess(filter, meta)))
                .sorted(Comparator.comparingLong(Meta::getCreateMs))
                .toList();
        if (!waiting.isEmpty()) {
            final String listed = String.join(", ", waiting.stream()
                    .map(meta -> meta.getId() + " (" + StreamProcessing.iso(meta.getCreateMs()) + ")")
                    .toList());
            throw new ToolRefusal("A filter on feed " + feed + " for " + streamType + " created after "
                                  + StreamProcessing.iso(bound) + " selects none of the feed's streams still to "
                                  + "process, which were all created before then: " + listed + ". To process the "
                                  + "sample, give stream_ids; for the whole feed, created_after at or before the "
                                  + "earliest of them.");
        }
        return new FeedBounds(bound, said);
    }

    /// When the feed doc of that name was created, or empty when there is none (or it can't be read).
    ///
    /// Stream data spells a feed as Stroom first saw it: when no feed has that exact name, the one feed spelt the
    /// same whatever its case is the feed (its earlier namesake, deleted, gave the spelling).
    ///
    /// @param feed A feed's name.
    /// @return When it was made, in milliseconds.
    Optional<Long> feedCreatedMs(final String feed) {
        try {
            Optional<DocRef> ref = feedNames.exact(feed);
            if (ref.isEmpty()) {
                final List<DocRef> twins = feedNames.feedsNamed(feed);
                ref = twins.size() == 1
                        ? Optional.of(twins.getFirst())
                        : Optional.empty();
            }
            return ref.flatMap(found -> documentAccess.read(found, FeedDoc.class))
                    .map(FeedDoc::getCreateTimeMs);
        } catch (final RuntimeException e) {
            return Optional.empty();
        }
    }

    /// Seen (Gemma, VS Code): the same feed-wide filter made twice, neither selecting anything
    /// (`_refuse_duplicate`). A feed by name (filters made before) or by reference is the same feed.
    ///
    /// @param filters    The pipeline's filters.
    /// @param expression What the new filter selects.
    /// @param minMs      Its lower bound, or null.
    /// @throws ToolRefusal if a filter of the pipeline already selects the same, from no later.
    static void refuseDuplicate(final List<ProcessorFilter> filters,
                                final ExpressionOperator expression,
                                final Long minMs) {
        final List<String> wanted = duplicateKey(expression);
        for (final ProcessorFilter filter : filters) {
            final Long earliest = filter.getMinMetaCreateTimeMs();
            if (duplicateKey(expressionOf(filter)).equals(wanted)
                && Objects.requireNonNullElse(earliest, 0L) <= Objects.requireNonNullElse(minMs, 0L)) {
                throw new ToolRefusal("Processor filter " + filter.getId() + " on this pipeline already selects "
                                      + "these streams" + (earliest != null && earliest != 0
                        ? " (created after " + StreamProcessing.iso(earliest) + ")"
                        : "") + ": processing_status shows what it matched. If it matched nothing, the streams are "
                                      + "not what it selects: a new filter that selects them is needed, not the same "
                                      + "one again.");
            }
        }
    }

    /// @param text An ISO time, e.g. `2026-10-09T21:30:00Z` (a time without an offset is UTC).
    /// @return It, in milliseconds since the epoch.
    /// @throws ToolRefusal if it isn't an ISO time.
    static long isoMs(final String text) {
        final String given = text.strip();
        try {
            return OffsetDateTime.parse(given).toInstant().toEpochMilli();
        } catch (final DateTimeParseException e) {
            // Not with an offset: a local time, or a date, taken as UTC.
        }
        try {
            return LocalDateTime.parse(given).toInstant(ZoneOffset.UTC).toEpochMilli();
        } catch (final DateTimeParseException e) {
            // Not a local time either.
        }
        try {
            return LocalDate.parse(given).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli();
        } catch (final DateTimeParseException e) {
            throw new ToolRefusal("created_after '" + text + "' is not an ISO time, e.g. 2026-10-09T21:30:00Z");
        }
    }

    /// @param field A stream field, e.g. `Id`.
    /// @param value Its value.
    /// @return The condition that the field equals the value (`_term`).
    static ExpressionTerm term(final String field, final Object value) {
        return ExpressionTerm.builder()
                .field(field)
                .condition(Condition.EQUALS)
                .value(String.valueOf(value))
                .build();
    }

    /// @param streamIds Streams.
    /// @return The expression selecting exactly them (Id EQUALS each, OR'd).
    static ExpressionOperator anyOf(final List<Long> streamIds) {
        final ExpressionOperator.Builder builder = ExpressionOperator.builder().op(Op.OR);
        streamIds.forEach(id -> builder.addTerm(term(ProcessorFilters.ID, id)));
        return builder.build();
    }

    /// @param items Conditions.
    /// @return All of them (AND).
    static ExpressionOperator allOf(final List<? extends ExpressionItem> items) {
        final ExpressionOperator.Builder builder = ExpressionOperator.builder().op(Op.AND);
        items.forEach(builder::addItem);
        return builder.build();
    }

    /// @param source The events pipeline whose Events are meant.
    /// @return The condition selecting only its output (Pipeline IS_DOC_REF), as `_pipeline_term`.
    static ExpressionTerm pipelineTerm(final DocRef source) {
        return ExpressionTerm.builder()
                .field(ProcessorFilters.PIPELINE)
                .condition(Condition.IS_DOC_REF)
                .docRef(new DocRef(PipelineDoc.TYPE, source.getUuid(), source.getName()))
                .build();
    }

    /// @param expression A filter's expression, or null.
    /// @return The streams it selects by id (Id EQUALS a number), at any depth (`_selected_ids`).
    static Set<Long> selectedIds(final ExpressionItem expression) {
        final Set<Long> ids = new TreeSet<>();
        if (expression instanceof final ExpressionOperator operator) {
            for (final ExpressionItem child : NullSafe.list(operator.getChildren())) {
                if (child instanceof ExpressionOperator) {
                    ids.addAll(selectedIds(child));
                } else if (child instanceof final ExpressionTerm term
                           && ProcessorFilters.ID.equals(term.getField())
                           && term.getCondition() == Condition.EQUALS
                           && term.getValue() != null
                           && DIGITS.matcher(term.getValue()).matches()) {
                    ids.add(Long.parseLong(term.getValue()));
                }
            }
        }
        return ids;
    }

    /// @param expression A filter's expression, or null.
    /// @return Its Pipeline conditions, at any depth (`_pipeline_terms`).
    static List<ExpressionTerm> pipelineTerms(final ExpressionItem expression) {
        return PipelineFeeds.filterTerms(expression).stream()
                .filter(term -> ProcessorFilters.PIPELINE.equals(term.getField()))
                .toList();
    }

    /// @param terms Conditions.
    /// @return The UUIDs of the pipelines their Pipeline conditions name.
    static List<String> pipelineUuids(final List<ExpressionTerm> terms) {
        return terms.stream()
                .filter(term -> ProcessorFilters.PIPELINE.equals(term.getField()))
                .map(term -> NullSafe.get(term.getDocRef(), DocRef::getUuid))
                .toList();
    }

    /// @param expression A filter's expression, or null.
    /// @return True if it has a Pipeline condition, at any depth (`_names_pipeline`).
    static boolean namesPipeline(final ExpressionItem expression) {
        return !pipelineTerms(expression).isEmpty();
    }

    /// @param filter A processor filter.
    /// @return What it selects, or null.
    static ExpressionOperator expressionOf(final ProcessorFilter filter) {
        return NullSafe.get(filter.getQueryData(), QueryData::getExpression);
    }

    // The terms of an expression as processing_status shows them, a feed by name or by reference the same.
    private static List<String> duplicateKey(final ExpressionOperator expression) {
        final List<String> key = new ArrayList<>();
        for (final String term : PipelineFeeds.terms(expression)) {
            final Matcher matcher = FEED_TERM.matcher(term);
            key.add(matcher.matches()
                    ? "Feed " + matcher.group(2).toLowerCase(Locale.ROOT)
                    : term);
        }
        key.sort(Comparator.naturalOrder());
        return key;
    }


    // --------------------------------------------------------------------------------


    /// A feed-wide filter's lower bound.
    ///
    /// @param minMs The bound, in milliseconds.
    /// @param said  How the scope says it, e.g. `2026-10-09T21:06:40.000Z (when the feed was created: ...)`.
    record FeedBounds(long minMs, String said) {

    }
}
