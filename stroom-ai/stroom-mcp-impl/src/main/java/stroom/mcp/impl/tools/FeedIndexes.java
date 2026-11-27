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

import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.ToolText;
import stroom.ai.impl.tools.streams.StreamReader;
import stroom.docref.DocRef;
import stroom.meta.shared.Meta;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/// A build of several feeds indexed one index per feed, as the user chose ([FeedIndexSplit]): each feed's events in
/// an index of its own, made by an indexing pipeline of its own writing to it, whose processor filters select that
/// feed's Events only, in the build and once promoted. One pipeline per index rather than one pipeline filling a
/// `{feed}` index name: it works for Lucene and Elasticsearch alike; each index's readers are set on its own index doc,
/// which is what the choice is for; each step the build takes already works a pipeline and an index at a time (its
/// index template, its verification, its filter at promotion); and in the build a `{feed}` name would be filled with
/// the test feed's name, as the build's Events are on its test feed.
///
/// The feed an indexing pipeline is for is the feed its index is named for (an index of one per feed is named for
/// exactly one feed: [FeedIndexSplit]).
class FeedIndexes {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(FeedIndexes.class);

    private final BuildPlan plan;
    private final BuildRecords records;
    private final IndexingRuns indexingRuns;
    private final IndexTemplateAgreements agreements;
    private final StreamReader streamReader;

    /// @param plan         Gives the feeds a build is for.
    /// @param records      Keeps the user's choice of one index per feed.
    /// @param indexingRuns Says which Lucene index a pipeline writes to.
    /// @param agreements   Says which Elasticsearch index a pipeline writes to.
    /// @param streamReader Reads each Events stream's raw parent's feed.
    @Inject
    FeedIndexes(final BuildPlan plan,
                final BuildRecords records,
                final IndexingRuns indexingRuns,
                final IndexTemplateAgreements agreements,
                final StreamReader streamReader) {
        this.plan = plan;
        this.records = records;
        this.indexingRuns = indexingRuns;
        this.agreements = agreements;
        this.streamReader = streamReader;
    }

    /// @param build A build.
    /// @return The feeds it is for, where the user chose one index per feed; none otherwise.
    List<String> perFeed(final String build) {
        try {
            if (NullSafe.isBlankString(build)
                || !BuildRecords.INDEX_PER_FEED.equals(records.feedComparison(build).get("index"))) {
                return List.of();
            }
            final List<String> feeds = plan.buildFeeds(build);
            return feeds.size() < 2
                    ? List.of()
                    : feeds;
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("No per-feed indexing read for build {}: {}", build, e.getMessage()));
            return List.of();
        }
    }

    /// @param build        A build.
    /// @param pipelineUuid One of its indexing pipelines.
    /// @return The feed it indexes, where the user chose one index per feed: the one its index is named for; empty
    /// otherwise.
    Optional<String> feedOf(final String build, final String pipelineUuid) {
        final List<String> feeds = perFeed(build);
        if (feeds.isEmpty()) {
            return Optional.empty();
        }
        final List<String> named = FeedIndexSplit.feedsNamed(feeds, indexNames(pipelineUuid));
        return named.size() == 1
                ? Optional.of(named.getFirst())
                : Optional.empty();
    }

    /// For a build of one index per feed, the index an indexing pipeline is made for: refused unless it is named for
    /// one feed.
    ///
    /// @param build     The build.
    /// @param indexName The index's name (its doc's, and its Elasticsearch index's).
    /// @return The feed it is for; empty for a build not indexed one index per feed.
    /// @throws ToolRefusal if the build is indexed one per feed and the index names no feed, or several.
    Optional<String> feedOfIndex(final String build, final String indexName) {
        final List<String> feeds = perFeed(build);
        if (feeds.isEmpty()) {
            return Optional.empty();
        }
        final List<String> named = FeedIndexSplit.feedsNamed(feeds, indexName);
        if (named.size() != 1) {
            throw new ToolRefusal("The user chose one index per feed for build '" + build + "': each indexing "
                                  + "pipeline writes to one feed's index, named for it ('" + indexName + "' names "
                                  + (named.isEmpty()
                    ? "none"
                    : String.join(" and ", named)) + " of " + String.join(", ", feeds) + "). Make one indexing "
                                  + "pipeline for each feed's index. Nothing was made.");
        }
        return Optional.of(named.getFirst());
    }

    /// Refuses streams of another feed for an indexing pipeline of one feed's index: its documents would go into an
    /// index the user meant for that feed alone.
    ///
    /// @param build        The build.
    /// @param pipelineUuid The indexing pipeline.
    /// @param streamIds    The Events streams to process.
    /// @throws ToolRefusal if any is of another feed, naming them and the feed's own.
    void refuseOtherFeeds(final String build, final String pipelineUuid, final List<Long> streamIds) {
        final Optional<String> feed = feedOf(build, pipelineUuid);
        if (feed.isEmpty() || streamIds.isEmpty()) {
            return;
        }
        final List<String> others = new ArrayList<>();
        final List<Long> own = new ArrayList<>();
        for (final Long id : streamIds) {
            final String source = sourceFeed(id);
            if (source != null && !source.equalsIgnoreCase(feed.get())) {
                others.add(id + " (" + source + ")");
            } else {
                own.add(id);
            }
        }
        if (!others.isEmpty()) {
            throw new ToolRefusal("This indexing pipeline writes " + feed.get() + "'s index (one index per feed, as "
                                  + "the user chose): stream(s) " + String.join(", ", others) + " are of another "
                                  + "feed, whose own indexing pipeline indexes them. Give only " + feed.get()
                                  + "'s Events streams" + (own.isEmpty()
                    ? ""
                    : " (" + ToolText.repr(own) + ")") + ". Nothing was processed.");
        }
    }

    /// For a build of one index per feed, each indexing pipeline's filters at promotion select its own feed's Events
    /// only (a Feed term added to its selection by the Events' source pipeline).
    ///
    /// @param build The build.
    /// @param plan  The promotion's filters, as [PromotionProcessing#promotionProcessing] planned them.
    /// @return Them with each such pipeline's filter narrowed to its feed; as they were otherwise.
    List<PromotionProcessing.Entry> narrowed(final String build, final List<PromotionProcessing.Entry> plan) {
        if (perFeed(build).isEmpty()) {
            return plan;
        }
        final List<PromotionProcessing.Entry> out = new ArrayList<>();
        for (final PromotionProcessing.Entry entry : plan) {
            final Optional<String> feed = feedOf(build, entry.pipeline().getUuid());
            out.add(feed.isPresent()
                    ? new PromotionProcessing.Entry(entry.pipeline(), List.of(feed.get()), entry.streamType(),
                            entry.extraTerms())
                    : entry);
        }
        return out;
    }

    // The names of the index a pipeline writes to: its Lucene index doc's, or its Elasticsearch index's.
    private String indexNames(final String pipelineUuid) {
        try {
            final Optional<DocRef> lucene = indexingRuns.luceneIndex(pipelineUuid);
            if (lucene.isPresent()) {
                return NullSafe.string(lucene.get().getName());
            }
            return agreements.elasticDestination(pipelineUuid)
                    .map(destination -> String.valueOf(destination.get("index name")))
                    .orElse("");
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("Pipeline {}'s index not read: {}", pipelineUuid, e.getMessage()));
            return "";
        }
    }

    // The feed an Events stream is of: its raw parent's (the build's Events are on its test feed), else its own.
    private String sourceFeed(final long streamId) {
        try {
            final Meta meta = streamReader.meta(streamId);
            return meta.getParentMetaId() == null
                    ? meta.getFeedName()
                    : streamReader.meta(meta.getParentMetaId()).getFeedName();
        } catch (final RuntimeException e) {
            return null;
        }
    }

    /// @param feeds A build's feeds and the feed each of its indexing pipelines indexes.
    /// @return The feeds with no indexing pipeline yet, in order.
    static List<String> withoutPipeline(final List<String> feeds, final Map<String, String> feedOfPipeline) {
        return feeds.stream()
                .filter(feed -> feedOfPipeline.values().stream().noneMatch(feed::equalsIgnoreCase))
                .toList();
    }
}
