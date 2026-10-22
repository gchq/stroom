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

import stroom.ai.impl.tools.StreamReader;
import stroom.docref.DocRef;
import stroom.feed.shared.FeedDoc;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaFields;
import stroom.meta.shared.MetaRow;
import stroom.pipeline.shared.PipelineDoc;
import stroom.processor.shared.ProcessorFilter;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/// The streams to prove a rebuilt mapping on, and where they came from (the Stroom MCP server's `tools/rebuild.py`
/// `sample_streams`): the pipeline's original samples (the stream ids of the processor filters the tools made for
/// them, those still there), else its feed's newest raw streams. Asked for by the user: the original sample data,
/// without the agent having to find it.
class SampleStreams {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SampleStreams.class);

    /// The raw stream types a sample can be.
    static final Set<String> RAW_TYPES = Set.of("Raw Events", "Raw Reference");
    /// The most newest raw streams taken from a feed.
    static final int NEWEST = 4;

    private final ProcessorFilters processorFilters;
    private final PipelineFeeds pipelineFeeds;
    private final StreamReader streamReader;
    private final WriteGuard guard;

    @Inject
    SampleStreams(final ProcessorFilters processorFilters,
                  final PipelineFeeds pipelineFeeds,
                  final StreamReader streamReader,
                  final WriteGuard guard) {
        this.processorFilters = processorFilters;
        this.pipelineFeeds = pipelineFeeds;
        this.streamReader = streamReader;
        this.guard = guard;
    }

    /// @param pipelineUuid A pipeline.
    /// @return Its sample streams, and which they are.
    /// @throws ToolRefusal if it has none and no feed of its filters or its build has raw streams.
    Found sampleStreams(final String pipelineUuid) {
        final List<ProcessorFilter> filters = processorFilters.all().stream()
                .filter(filter -> pipelineUuid.equals(filter.getPipelineUuid()))
                .sorted(Comparator.comparingInt(filter -> Objects.requireNonNullElse(filter.getId(), 0)))
                .toList();
        final List<Long> ids = new ArrayList<>();
        for (final ProcessorFilter filter : filters) {
            for (final ExpressionTerm term : ProcessorFilters.terms(filter)) {
                final String value = NullSafe.string(term.getValue()).strip();
                if ("Id".equals(term.getField()) && Condition.EQUALS.equals(term.getCondition())
                    && !value.isEmpty() && value.chars().allMatch(Character::isDigit)
                    && !ids.contains(Long.parseLong(value))) {
                    ids.add(Long.parseLong(value));
                }
            }
        }
        if (!ids.isEmpty()) {
            final List<Long> kept = ids.stream().filter(this::isRaw).toList();
            if (!kept.isEmpty()) {
                return new Found(kept, "the pipeline's original sample streams (its sample processor filters)");
            }
        }
        // Why the original samples aren't used: say which, a pipeline only ever stepped never had sample filters.
        final String why = !ids.isEmpty()
                ? "its sample streams are gone"
                : !filters.isEmpty()
                        ? "its sample processor filters are gone"
                        : "it has no sample processor filters: only ever stepped";
        final List<String> feeds = new ArrayList<>();
        final Map<Object, Object> known = new HashMap<>();
        for (final ProcessorFilter filter : filters) {
            pipelineFeeds.filterFeeds(ProcessorFilters.terms(filter), known, false).stream()
                    .filter(name -> !feeds.contains(name))
                    .forEach(feeds::add);
        }
        if (feeds.isEmpty()) {
            // Stepped but never processed: no filters. The feeds of the build the pipeline is in.
            buildFeeds(pipelineUuid).forEach(feeds::add);
        }
        for (final String feed : feeds) {
            final List<Meta> rows = newest(feed);
            if (!rows.isEmpty()) {
                return new Found(rows.stream().map(Meta::getId).toList(), "the newest raw streams of feed '"
                                                                          + NullSafe.nonBlankStringElse(
                        rows.getFirst().getFeedName(), feed) + "' (" + why + ")");
            }
        }
        throw new ToolRefusal("Give stream_ids: the pipeline's sample raw streams. "
                              + Character.toUpperCase(why.charAt(0)) + why.substring(1) + ", and no feed of its "
                              + "filters or its build has raw streams.");
    }

    private boolean isRaw(final long id) {
        try {
            return streamReader.metaRow(id)
                    .map(MetaRow::getMeta)
                    .map(Meta::getTypeName)
                    .filter(RAW_TYPES::contains)
                    .isPresent();
        } catch (final RuntimeException e) {
            return false;
        }
    }

    private List<String> buildFeeds(final String pipelineUuid) {
        final Optional<String> build = guard.buildOf(new DocRef(PipelineDoc.TYPE, pipelineUuid));
        if (build.isEmpty()) {
            return List.of();
        }
        try {
            return guard.folderContents(build.get()).stream()
                    .filter(doc -> FeedDoc.TYPE.equals(doc.type()))
                    .map(WriteGuard.BuildDoc::name)
                    .toList();
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> "Build " + build.get() + " can't be listed: " + e.getMessage());
            return List.of();
        }
    }

    private List<Meta> newest(final String feed) {
        final ExpressionOperator expression = ExpressionOperator.builder().op(Op.AND)
                .addTextTerm(MetaFields.FEED, Condition.EQUALS, feed)
                .addTextTerm(MetaFields.TYPE, Condition.EQUALS, "Raw Events")
                .build();
        return NullSafe.list(NullSafe.get(streamReader.find(expression, NEWEST, true), ResultPage::getValues))
                .stream()
                .map(MetaRow::getMeta)
                .filter(Objects::nonNull)
                .toList();
    }


    // --------------------------------------------------------------------------------


    /// Sample streams.
    ///
    /// @param streamIds The streams.
    /// @param which     Where they came from, for the reply.
    record Found(List<Long> streamIds, String which) {

    }
}
