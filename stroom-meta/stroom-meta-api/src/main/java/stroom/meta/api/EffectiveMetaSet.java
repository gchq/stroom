/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.meta.api;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Hold an immutable ordered set of {@link EffectiveMeta} items for the same feed and type.
 * Ordered by {@link EffectiveMeta#getEffectiveMs()} (ascending).
 * De-dups on creation so contains exactly one metaId per effectiveTimeMs.
 */
public class EffectiveMetaSet implements Iterable<EffectiveMeta> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(EffectiveMetaSet.class);

    // Exactly one metaId per effectiveTimeMs (see de-duping in add(), so only
    // comparator only needs to work on effectiveTimeMs
    private static final Comparator<EffectiveMeta> EFF_TIME_COMPARATOR = Comparator.comparingLong(
            EffectiveMeta::getEffectiveMs);

    private static final EffectiveMetaSet EMPTY = new EffectiveMetaSet(
            null,
            null,
            Collections.emptyNavigableSet());

    private final NavigableSet<EffectiveMeta> effectiveMetas;
    /**
     * Hold all the meta IDs as a sorted array, so we can do fast equality checking in the
     * EffectiveStreamInternPool, It is also used to compute the cached hash.
     */
    private final long[] sortedMetaIds;

    // These are here primarily for logging and for ensuring that all items in the set share
    // the same feed and type
    private final String feed;
    private final String type;

    /**
     * Cache the hash code as we are pretty much guaranteed to call hashCode in
     * EffectiveStreamInternPool
     */
    private final int hash;

    private EffectiveMetaSet(final String feedName,
                             final String typeName,
                             final NavigableSet<EffectiveMeta> effectiveMetas) {
        this.effectiveMetas = effectiveMetas;
        this.feed = feedName;
        this.type = typeName;
        this.sortedMetaIds = buildSortedIds(this.effectiveMetas);
        this.hash = buildHash(this.sortedMetaIds);
    }

    private EffectiveMetaSet(final Builder builder) {
        this.effectiveMetas = builder.effectiveMetas;
        this.feed = builder.feed;
        this.type = builder.type;

        // Hold the stream IDs as a sorted array for easier equality checking of this EffectiveMetaSet
        this.sortedMetaIds = buildSortedIds(this.effectiveMetas);
        this.hash = buildHash(this.sortedMetaIds);
    }

    public static EffectiveMetaSet singleton(final EffectiveMeta effectiveMeta) {
        Objects.requireNonNull(effectiveMeta);
        return new Builder(effectiveMeta.getFeedName(), effectiveMeta.getTypeName())
                .add(effectiveMeta)
                .build();
    }

    public static EffectiveMetaSet of(final EffectiveMeta... effectiveMetas) {
        return of(NullSafe.asList(effectiveMetas));
    }

    public static EffectiveMetaSet of(final Collection<EffectiveMeta> effectiveMetaList) {
        if (NullSafe.isEmptyCollection(effectiveMetaList)) {
            return EMPTY;
        } else {
            Builder builder = null;
            for (final EffectiveMeta effectiveMeta : effectiveMetaList) {
                if (builder == null) {
                    builder = EffectiveMetaSet.builder(effectiveMeta.getFeedName(), effectiveMeta.getTypeName());
                }
                builder.add(effectiveMeta);
            }
            return builder != null
                    ? builder.build()
                    : EMPTY;
        }
    }

    private long[] buildSortedIds(final NavigableSet<EffectiveMeta> set) {
        return set.stream()
                .mapToLong(EffectiveMeta::getId)
                .sorted()
                .toArray();
    }

    public static EffectiveMetaSet empty() {
        return EMPTY;
    }

    public String getFeedName() {
        return feed;
    }

    public String getTypeName() {
        return type;
    }

    public int size() {
        return effectiveMetas.size();
    }

    public boolean isEmpty() {
        return effectiveMetas.isEmpty();
    }

    public void clear() {
        effectiveMetas.clear();
    }

    /**
     * @return A {@link Stream} of {@link EffectiveMeta} items in ascending order
     * of {@link EffectiveMeta#getEffectiveMs()}
     */
    public Stream<EffectiveMeta> stream() {
        return effectiveMetas.stream();
    }

    /**
     * @return An unmodifiable list of {@link EffectiveMeta} in ascending order
     * of {@link EffectiveMeta#getEffectiveMs()}
     */
    public List<EffectiveMeta> asList() {
        return stream().toList();
    }

    /**
     * @return An unmodifiable set of {@link EffectiveMeta} in ascending order
     * of {@link EffectiveMeta#getEffectiveMs()}
     */
    public SortedSet<EffectiveMeta> asSet() {
        return Collections.unmodifiableSortedSet(
                stream().collect(Collectors.toCollection(EffectiveMetaSet::createTreeSet)));
    }

    @Override
    public boolean equals(final Object object) {
        // CUSTOM equals - only care about the metaIds
        if (this == object) {
            return true;
        }
        if (object == null || getClass() != object.getClass()) {
            return false;
        }
        final EffectiveMetaSet that = (EffectiveMetaSet) object;
        return Arrays.equals(this.sortedMetaIds, that.sortedMetaIds);
    }

    @Override
    public int hashCode() {
        return hash;
    }

    private int buildHash(final long[] sortedMetaIds) {
        return Arrays.hashCode(sortedMetaIds);
    }

    @Override
    public String toString() {
        return "EffectiveMetaSet{" +
               "feed='" + feed + '\'' +
               ", type='" + type + '\'' +
               ", set=\n" + effectiveStreamsToString(effectiveMetas) +
               '}';
    }

    private static String effectiveStreamsToString(final Collection<EffectiveMeta> effectiveStreams) {
        if (effectiveStreams == null) {
            return "";
        } else {
            final List<String> sortedStringMetas = effectiveStreams.stream()
                    .sorted(Comparator.comparing(EffectiveMeta::getEffectiveMs))
                    .map(LogUtil::toStringWithoutClassName)
                    .toList();

            Stream<String> stream = sortedStringMetas.stream();

            final int limit = 20;
            final int size = sortedStringMetas.size();
            if (size > limit) {
                final String lastMetaStr = sortedStringMetas.get(size - 1);

                stream = Stream.concat(
                        stream.limit(limit - 1),
                        Stream.of(
                                "...TRUNCATED...",
                                lastMetaStr));
            }
            return stream
                    .map(str -> "  " + str)
                    .collect(Collectors.joining("\n"));
        }
    }

    public static Builder builder(final String feedName, final String typeName) {
        return new Builder(feedName, typeName);
    }

    /**
     * @return The {@link EffectiveMeta} with the oldest effectiveTimeMs
     */
    public Optional<EffectiveMeta> first() {
        try {
            return Optional.ofNullable(effectiveMetas.first());
        } catch (final NoSuchElementException e) {
            return Optional.empty();
        }
    }

    /**
     * @return The {@link EffectiveMeta} with the most recent effectiveTimeMs
     */
    public Optional<EffectiveMeta> last() {
        try {
            return Optional.ofNullable(effectiveMetas.last());
        } catch (final NoSuchElementException e) {
            return Optional.empty();
        }
    }

    /**
     * Find the latest {@link EffectiveMeta} in effectiveMetaSet that is before or equal to timeMs.
     */
    public Optional<EffectiveMeta> findLatestBefore(final long timeMs) {
        if (isEmpty()) {
            return Optional.empty();
        } else {
            return Optional.ofNullable(effectiveMetas.floor(EffectiveMeta.asFloorKey(timeMs)));
        }
    }


    public boolean contains(final EffectiveMeta effectiveMeta) {
        return effectiveMetas.contains(effectiveMeta);
    }

    public boolean containsAll(final Collection<EffectiveMeta> collection) {
        return effectiveMetas.containsAll(collection);
    }

    /**
     * Create a {@link Stream} {@link Collector} for creating an {@link EffectiveMetaSet}
     * from a {@link Stream} of {@link EffectiveMeta}.
     *
     * @param feedName The feed name for all {@link EffectiveMeta} items.
     * @param typeName The stream type name for all {@link EffectiveMeta} items.
     */
    public static Collector<EffectiveMeta, Builder, EffectiveMetaSet> collector(final String feedName,
                                                                                final String typeName) {
        return new Collector<>() {
            @Override
            public Supplier<Builder> supplier() {
                return () ->
                        new Builder(feedName, typeName);
            }

            @Override
            public BiConsumer<Builder, EffectiveMeta> accumulator() {
                return Builder::add;
            }

            @Override
            public BinaryOperator<Builder> combiner() {
                return Builder::addAll;
            }

            @Override
            public Function<Builder, EffectiveMetaSet> finisher() {
                return Builder::build;
            }

            @Override
            public Set<Characteristics> characteristics() {
                return Collections.emptySet();
            }
        };
    }

    @Override
    public Iterator<EffectiveMeta> iterator() {
        return Collections.unmodifiableSortedSet(effectiveMetas).iterator();
    }

    private static NavigableSet<EffectiveMeta> createTreeSet() {
        // We control the comparator so EffectiveMetaSet can be sure how things are
        // being sorted/compared.
        return new TreeSet<>(EFF_TIME_COMPARATOR);
    }


    // --------------------------------------------------------------------------------


    public static final class Builder {

        private NavigableSet<EffectiveMeta> effectiveMetas = null;
        private Map<Long, EffectiveMeta> effectiveTimeToMetaMap = null;
        private final String feed;
        private final String type;

        private Builder(final String feedName, final String typeName) {
            // Many EffectiveMeta objects will share the same feed/type (which help with debugging)
            // so intern them to reduce mem use.
            this.feed = Objects.requireNonNull(feedName).intern();
            this.type = Objects.requireNonNull(typeName).intern();
        }

        private Builder addAll(final Builder other) {
            if (other != null) {
                if (NullSafe.hasEntries(effectiveTimeToMetaMap)) {
                    if (!Objects.equals(this.feed, other.feed)) {
                        throw new RuntimeException(LogUtil.message(
                                "Feed mismatch. {} vs {}", this.feed, other.feed));
                    }
                    if (!Objects.equals(this.type, other.type)) {
                        throw new RuntimeException(LogUtil.message(
                                "Type mismatch. {} vs {}", this.type, other.type));
                    }
                    other.effectiveTimeToMetaMap.forEach((timeMs, effectiveMeta) -> {
                        add(effectiveMeta.getId(), timeMs);
                    });
                }
            }
            return this;
        }

        /**
         * Add an {@link EffectiveMeta} to this set. It will de-dup items that have the same
         * {@link EffectiveMeta#getEffectiveMs()}, with the winner being the one with the highest
         * {@link EffectiveMeta#getId()}.
         *
         * @throws RuntimeException When the feed or type in the effectiveMeta do not match those
         *                          on this {@link Builder}.
         */
        public Builder add(final EffectiveMeta effectiveMeta) {
            if (effectiveMeta != null) {
                if (!this.feed.equals(effectiveMeta.getFeedName())) {
                    throw new RuntimeException(LogUtil.message(
                            "Feed in effective meta {} doesn't match feed ({}) for this set",
                            effectiveMeta, this.feed));
                }
                if (!this.type.equals(effectiveMeta.getTypeName())) {
                    throw new RuntimeException(LogUtil.message(
                            "Type in effective meta {} doesn't match type ({}) for this set",
                            effectiveMeta, this.type));
                }
                add(effectiveMeta.getId(), effectiveMeta.getEffectiveMs());
            }
            return this;
        }

        /**
         * Add an {@link EffectiveMeta} to this set using its metaID and effectiveTimeMs.
         * It will de-dup items that have the same
         * {@link EffectiveMeta#getEffectiveMs()}, with the winner being the one with the highest
         * {@link EffectiveMeta#getId()}.
         */
        public Builder add(final long metaId, final long effectiveTimeMs) {
            final EffectiveMeta effectiveMeta = new EffectiveMeta(metaId, feed, type, effectiveTimeMs);

            if (effectiveTimeToMetaMap == null) {
                effectiveTimeToMetaMap = new HashMap<>();
            }

            // De-dup based on the effective time. Highest metaId is the winner
            effectiveTimeToMetaMap.merge(
                    effectiveMeta.getEffectiveMs(),
                    effectiveMeta,
                    (existingMeta, newMeta) -> {
                        final EffectiveMeta resultMeta = newMeta.getId() > existingMeta.getId()
                                ? newMeta
                                : effectiveMeta;

                        LOGGER.warn("Reference streams [{}] and [{}] from feed '{}' found with the same " +
                                    "effective time {}. Stroom cannot know which is the preferred " +
                                    "stream so the one with the highest stream ID ({}) will be " +
                                    "used and the other ignored.",
                                existingMeta.getId(),
                                newMeta.getId(),
                                feed,
                                effectiveMeta.getEffectiveTime(),
                                resultMeta.getId());

                        return resultMeta;
                    });
            return this;
        }

        public EffectiveMetaSet build() {
            if (this.effectiveTimeToMetaMap == null) {
                return EMPTY;
            } else {
                // Build the TreeSet from our de-duped data.
                // Ad we have de-duped we only need to compare on time, not metaId as well.
                this.effectiveMetas = createTreeSet();
                this.effectiveMetas.addAll(effectiveTimeToMetaMap.values());

                return new EffectiveMetaSet(this);
            }
        }

        @Override
        public String toString() {
            return "Builder{" +
                   "effectiveMetas=" + effectiveMetas +
                   ", effectiveTimeToMetaMap.size=" + effectiveTimeToMetaMap.size() +
                   ", feed='" + feed + '\'' +
                   ", type='" + type + '\'' +
                   '}';
        }
    }
}
