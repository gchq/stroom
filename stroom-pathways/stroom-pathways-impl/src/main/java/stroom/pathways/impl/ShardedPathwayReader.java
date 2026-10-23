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

package stroom.pathways.impl;

import stroom.bytebuffer.ByteBufferUtils;
import stroom.pathways.shared.FindPathwayCriteria;
import stroom.pathways.shared.FindPathwayMutationCriteria;
import stroom.pathways.shared.PathwayMutationResultPage;
import stroom.pathways.shared.PathwayResultPage;
import stroom.pathways.shared.PathwaySummary;
import stroom.pathways.shared.PathwaysDoc;
import stroom.pathways.shared.otel.trace.NanoTime;
import stroom.pathways.shared.pathway.Pathway;
import stroom.pathways.shared.pathway.PathwayMutation;
import stroom.pathways.shared.pathway.PathwayUsage;
import stroom.planb.impl.dao.ShardKeyRouter;
import stroom.planb.impl.dao.trace.PathwaysDb;
import stroom.planb.shared.SharedFileStoreSettings;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.CriteriaFieldSort;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PageRequest;
import stroom.util.shared.PageResponse;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Answers a pathway search from every shard of a document's model.
 *
 * <p>A pathway is keyed on its operation name and that name decides its shard, so no pathway appears
 * in two shards and nothing has to be combined — only gathered and put back in order.
 *
 * <p>Done in two passes. The first reads names only, which costs no deserialising, and sorts them to
 * get the one order a page can be taken from. The second fetches just the page, and knows which shard
 * to ask because the name says so. The alternative — deserialising every match to find out which
 * belong on the page — would read the whole model to return twenty rows.
 */
@Singleton
public class ShardedPathwayReader {

    private static final Comparator<PathwaySummary> BY_NAME =
            Comparator.comparing(PathwaySummary::getName, Comparator.nullsFirst(Comparator.naturalOrder()));

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ShardedPathwayReader.class);

    private final PathwaysShardStore shardStore;
    private final PathwaySerde pathwaySerde;
    private final MutationLog mutationLog;

    @Inject
    public ShardedPathwayReader(final PathwaysShardStore shardStore,
                                final PathwaySerde pathwaySerde,
                                final MutationLog mutationLog) {
        this.shardStore = shardStore;
        this.pathwaySerde = pathwaySerde;
        this.mutationLog = mutationLog;
    }

    public PathwayResultPage findPathways(final PathwaysDoc doc, final FindPathwayCriteria criteria) {
        final SharedFileStoreSettings settings = doc.getSharedFileStore();
        if (settings == null || NullSafe.isBlankString(settings.getSharedPath())) {
            // Nothing configured, so nothing was ever written for it.
            return new PathwayResultPage(List.of(), PageResponse.empty());
        }
        final int shardCount = shardCountOf(settings);
        final PageRequest pageRequest = criteria.getPageRequest();

        // Every matching pathway, not just the ones on the page: a sort on anything but the name has
        // to see them all before it can say which belong on it. Only each one's header is read, and
        // the scan of the keys has that in hand already.
        final List<PathwaySummary> summaries = new ArrayList<>();
        for (int shard = 0; shard < shardCount; shard++) {
            summaries.addAll(matchingSummaries(doc, shard, criteria.getFilter()));
        }
        summaries.sort(comparator(criteria.getSortList()));

        final List<PathwaySummary> page = summaries.stream()
                .skip(pageRequest.getOffset())
                .limit(pageRequest.getLength())
                .toList();

        final PageResponse pageResponse = PageResponse
                .builder()
                .offset(pageRequest.getOffset())
                .length(page.size())
                .total((long) summaries.size())
                .exact(true)
                .build();
        return new PathwayResultPage(page, pageResponse);
    }

    // What one shard holds that the filter accepts. The name is taken from the key so a rejected
    // pathway costs nothing beyond that; only an accepted one has its header decoded.
    private List<PathwaySummary> matchingSummaries(final PathwaysDoc doc,
                                                   final int shard,
                                                   final String filter) {
        final List<PathwaySummary> summaries = new ArrayList<>();
        readShard(doc, shard, db -> {
            db.getPathways().iterate((key, val) -> {
                final String name = ByteBufferUtils.toString(key);
                if (NullSafe.isBlankString(filter) || name.contains(filter)) {
                    summaries.add(pathwaySerde.readSummary(val));
                }
            });
            return summaries;
        });
        return summaries;
    }

    // Orders by the columns the grid was sorted on, in the order they were clicked. Falls back to the
    // name where nothing was asked for, which is where the grid starts.
    private static Comparator<PathwaySummary> comparator(final List<CriteriaFieldSort> sortList) {
        Comparator<PathwaySummary> comparator = null;
        for (final CriteriaFieldSort sort : NullSafe.list(sortList)) {
            final Comparator<PathwaySummary> field = fieldComparator(sort);
            if (field != null) {
                comparator = comparator == null
                        ? field
                        : comparator.thenComparing(field);
            }
        }
        return comparator == null
                ? BY_NAME
                : comparator;
    }

    private static Comparator<PathwaySummary> fieldComparator(final CriteriaFieldSort sort) {
        final Comparator<PathwaySummary> comparator = switch (NullSafe.string(sort.getId())) {
            case PathwaySummary.FIELD_NAME -> sort.isIgnoreCase()
                    ? Comparator.comparing(PathwaySummary::getName,
                            Comparator.nullsFirst(String.CASE_INSENSITIVE_ORDER))
                    : BY_NAME;
            case PathwaySummary.FIELD_CREATE_TIME -> byTime(PathwaySummary::getCreateTime);
            case PathwaySummary.FIELD_UPDATE_TIME -> byTime(PathwaySummary::getUpdateTime);
            case PathwaySummary.FIELD_LAST_USED_TIME -> byTime(PathwaySummary::getLastUsedTime);
            case PathwaySummary.FIELD_TIMES_USED -> Comparator.comparingLong(PathwaySummary::getTimesUsed);
            case PathwaySummary.FIELD_TIMES_UPDATED -> Comparator.comparingLong(
                    PathwaySummary::getTimesUpdated);
            case PathwaySummary.FIELD_NODES -> Comparator.comparingInt(PathwaySummary::getNodes);
            case PathwaySummary.FIELD_PATHS -> Comparator.comparingInt(PathwaySummary::getPaths);
            case PathwaySummary.FIELD_SIZE -> Comparator.comparingLong(PathwaySummary::getSizeBytes);
            // A column the reader knows nothing about orders on nothing, rather than throwing away a
            // page of results.
            default -> null;
        };
        return comparator == null || !sort.isDesc()
                ? comparator
                : comparator.reversed();
    }

    // A pathway that has never recorded the time sorts before one that has, rather than blowing up.
    private static Comparator<PathwaySummary> byTime(final Function<PathwaySummary, NanoTime> time) {
        return Comparator.comparing(time, Comparator.nullsFirst(Comparator.naturalOrder()));
    }

    /**
     * How much each node had been used at each point this pathway's history can be wound back to.
     *
     * <p>Not paged and not sorted: a reading is kept only where a trace changed the model, so there
     * are as many as there are traces that taught it something, and the browser holds the lot to
     * colour the drawing wherever the reader winds it back to.
     */
    public List<PathwayUsage> findUsage(final PathwaysDoc doc,
                                        final FindPathwayMutationCriteria criteria) {
        final SharedFileStoreSettings settings = doc.getSharedFileStore();
        final String name = criteria.getPathwayName();
        if (settings == null || NullSafe.isBlankString(settings.getSharedPath())
            || NullSafe.isBlankString(name)) {
            return List.of();
        }

        final int shard = ShardKeyRouter.computeShardIndex(name, shardCountOf(settings));
        final List<PathwayUsage> usage = new ArrayList<>();
        readShard(doc, shard, db -> {
            usage.addAll(mutationLog.readUsage(db.getMutations(), name.getBytes(StandardCharsets.UTF_8)));
            return null;
        });
        return usage;
    }

    /**
     * The changes made to one learnt pathway. Only that pathway's shard is opened, and only its own
     * run of keys is walked, because the mutation key begins with the name it belongs to.
     */
    public PathwayMutationResultPage findMutations(final PathwaysDoc doc,
                                                   final FindPathwayMutationCriteria criteria) {
        final SharedFileStoreSettings settings = doc.getSharedFileStore();
        final String name = criteria.getPathwayName();
        if (settings == null || NullSafe.isBlankString(settings.getSharedPath())
            || NullSafe.isBlankString(name)) {
            return new PathwayMutationResultPage(List.of(), PageResponse.empty());
        }

        final int shard = ShardKeyRouter.computeShardIndex(name, shardCountOf(settings));
        final List<PathwayMutation> mutations = new ArrayList<>();
        readShard(doc, shard, db -> {
            mutations.addAll(mutationLog.read(db.getMutations(), name.getBytes(StandardCharsets.UTF_8)));
            return null;
        });

        mutations.sort(mutationComparator(criteria.getSortList()));

        final PageRequest pageRequest = criteria.getPageRequest();
        final List<PathwayMutation> page = mutations.stream()
                .skip(pageRequest.getOffset())
                .limit(pageRequest.getLength())
                .toList();
        final PageResponse pageResponse = PageResponse
                .builder()
                .offset(pageRequest.getOffset())
                .length(page.size())
                .total((long) mutations.size())
                .exact(true)
                .build();
        return new PathwayMutationResultPage(page, pageResponse);
    }

    // Ordered the way the view orders it, so the two cannot drift apart. Newest first where nothing
    // was asked for, which is what a list of recent changes wants.
    private static Comparator<PathwayMutation> mutationComparator(final List<CriteriaFieldSort> sortList) {
        Comparator<PathwayMutation> comparator = null;
        for (final CriteriaFieldSort sort : NullSafe.list(sortList)) {
            final Comparator<PathwayMutation> field =
                    PathwayMutation.comparator(sort.getId(), sort.isDesc());
            comparator = comparator == null
                    ? field
                    : comparator.thenComparing(field);
        }
        return comparator == null
                ? PathwayMutation.comparator(PathwayMutation.FIELD_TIME, true)
                : comparator;
    }

    /**
     * One learnt pathway in full, or empty where the document holds none by that name.
     *
     * <p>Separate from the list because the list does not want this: a pathway keeps every path it has
     * seen an operation take, so reading one to render a row is what a page of rows could not afford.
     */
    public Optional<Pathway> fetchPathway(final PathwaysDoc doc, final String name) {
        final SharedFileStoreSettings settings = doc.getSharedFileStore();
        if (settings == null || NullSafe.isBlankString(settings.getSharedPath())
            || NullSafe.isBlankString(name)) {
            return Optional.empty();
        }
        final int shard = ShardKeyRouter.computeShardIndex(name, shardCountOf(settings));
        final Pathway[] found = {null};
        readShard(doc, shard, db -> {
            withKey(name, keyBuffer -> db.getPathways().get(keyBuffer, valueBuffer -> {
                if (valueBuffer != null) {
                    found[0] = pathwaySerde.readPathway(valueBuffer);
                }
                return null;
            }));
            return null;
        });
        return Optional.ofNullable(found[0]);
    }

    private static void withKey(final String name, final Consumer<ByteBuffer> consumer) {
        final byte[] keyBytes = name.getBytes(StandardCharsets.UTF_8);
        final ByteBuffer keyBuffer = ByteBuffer.allocateDirect(keyBytes.length);
        keyBuffer.put(keyBytes).flip();
        consumer.accept(keyBuffer);
    }

    // A shard that cannot be read must not fail the whole search: the rest of the model is still worth
    // showing, and a shard nothing has applied to yet has no model at all, which is normal.
    private static int shardCountOf(final SharedFileStoreSettings settings) {
        return Math.max(1, settings.getShardCount());
    }

    private <R> void readShard(final PathwaysDoc doc,
                               final int shard,
                               final Function<PathwaysDb, R> work) {
        try {
            shardStore.readShard(doc, shard, work);
        } catch (final IOException | RuntimeException e) {
            LOGGER.error(() -> "Could not read shard " + shard + " of " + doc.getName()
                               + ", so its pathways are missing from this result: " + e.getMessage(), e);
        }
    }
}
