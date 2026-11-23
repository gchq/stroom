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

package stroom.ai.impl.tools;

import stroom.ai.api.ElasticKeyStreams.KeyCount;
import stroom.ai.api.ElasticKeyStreams.KeyRange;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/// How the streaming join cuts its key space for workers: into ranges of the first join field's values, so a key
/// never spans two ranges and each worker merges its range alone. From a sample of the largest side's keys (every
/// Nth by its events, so the ranges hold about the same rows, not an even cut of the alphabet), or, where a small
/// side is pushed down, from that side's own keys, each range then read in batches of its keys under
/// Elasticsearch's terms limit (each batch a range of its own, in key order).
final class KeyRanges {

    private KeyRanges() {
        // Static helpers only.
    }

    /// @param boundaries Values of the first join field, each the last of a range, in key order.
    /// @return The ranges: up to the first boundary, between each two, and after the last; one range of every key
    ///         when there are none.
    static List<Planned> fromBoundaries(final List<Object> boundaries) {
        final List<Planned> ranges = new ArrayList<>();
        Object after = null;
        for (final Object boundary : boundaries) {
            if (boundary == null || Objects.equals(boundary, after)) {
                continue;
            }
            final KeyRange range = new KeyRange(after, boundary);
            ranges.add(new Planned(range, List.of(new Chunk(range, List.of()))));
            after = boundary;
        }
        final KeyRange last = new KeyRange(after, null);
        ranges.add(new Planned(last, List.of(new Chunk(last, List.of()))));
        return ranges;
    }

    /// The ranges of a pushed-down side's keys: its keys cut into about `workers` parts of as many keys (a first
    /// field's value never split), each part in batches of at most `maxTerms` keys, each batch with its keys' values
    /// per join field as terms. The first range starts at the first key and the last runs to the end, so together
    /// they cover every key.
    ///
    /// @param keys     The side's keys, in key order.
    /// @param workers  How many ranges at most.
    /// @param maxTerms The most keys in one batch.
    /// @return The ranges, in key order; none if there are no keys.
    static List<Planned> fromKeys(final List<KeyCount> keys, final int workers, final int maxTerms) {
        final List<List<KeyCount>> groups = groupsByFirst(keys);
        if (groups.isEmpty()) {
            return List.of();
        }
        final int parts = Math.clamp(workers, 1, groups.size());
        // Cut after the group that takes the running count to each part's share of the keys.
        final List<List<List<KeyCount>>> partGroups = new ArrayList<>();
        List<List<KeyCount>> part = new ArrayList<>();
        long counted = 0;
        for (int g = 0; g < groups.size(); g++) {
            part.add(groups.get(g));
            counted += groups.get(g).size();
            final double share = (double) (partGroups.size() + 1) * keys.size() / parts;
            final boolean groupsLeft = groups.size() - g - 1 >= parts - partGroups.size() - 1;
            if (partGroups.size() < parts - 1 && counted >= share && groupsLeft && g < groups.size() - 1) {
                partGroups.add(part);
                part = new ArrayList<>();
            }
        }
        partGroups.add(part);

        final List<Planned> ranges = new ArrayList<>();
        Object after = null;
        for (int p = 0; p < partGroups.size(); p++) {
            final List<List<KeyCount>> groupsOfPart = partGroups.get(p);
            final List<Chunk> chunks = new ArrayList<>();
            Object chunkAfter = after;
            List<KeyCount> batch = new ArrayList<>();
            for (final List<KeyCount> group : groupsOfPart) {
                if (!batch.isEmpty() && batch.size() + group.size() > maxTerms) {
                    final Object last = first(batch.getLast());
                    chunks.add(new Chunk(new KeyRange(chunkAfter, last), terms(batch)));
                    chunkAfter = last;
                    batch = new ArrayList<>();
                }
                batch.addAll(group);
            }
            final boolean lastPart = p == partGroups.size() - 1;
            final Object upTo = lastPart
                    ? null
                    : first(batch.getLast());
            chunks.add(new Chunk(new KeyRange(chunkAfter, upTo), terms(batch)));
            ranges.add(new Planned(new KeyRange(after, upTo), chunks));
            after = upTo;
        }
        return ranges;
    }

    // The keys in groups of one first-field value each, in key order.
    private static List<List<KeyCount>> groupsByFirst(final List<KeyCount> keys) {
        final List<List<KeyCount>> groups = new ArrayList<>();
        for (final KeyCount key : keys) {
            if (!groups.isEmpty() && Objects.equals(first(groups.getLast().getLast()), first(key))) {
                groups.getLast().add(key);
            } else {
                final List<KeyCount> group = new ArrayList<>();
                group.add(key);
                groups.add(group);
            }
        }
        return groups;
    }

    private static Object first(final KeyCount key) {
        return key.key().isEmpty()
                ? null
                : key.key().getFirst();
    }

    /// @param keys Keys.
    /// @return Their distinct values per join field, in order.
    static List<List<Object>> terms(final List<KeyCount> keys) {
        if (keys.isEmpty()) {
            return List.of();
        }
        final int fields = keys.getFirst().key().size();
        final List<Set<Object>> values = new ArrayList<>();
        for (int i = 0; i < fields; i++) {
            values.add(new LinkedHashSet<>());
        }
        for (final KeyCount key : keys) {
            for (int i = 0; i < fields && i < key.key().size(); i++) {
                if (key.key().get(i) != null) {
                    values.get(i).add(key.key().get(i));
                }
            }
        }
        return values.stream()
                .map(set -> Collections.unmodifiableList(new ArrayList<>(set)))
                .toList();
    }


    // --------------------------------------------------------------------------------


    /// One worker's key range, read in chunks.
    ///
    /// @param range  The range.
    /// @param chunks Its chunks, in key order: the range itself, or batches of pushed-down keys.
    record Planned(KeyRange range, List<Chunk> chunks) {

        /// Copies the list.
        Planned {
            chunks = List.copyOf(chunks);
        }
    }


    // --------------------------------------------------------------------------------


    /// Part of a range read at once.
    ///
    /// @param range The chunk's range.
    /// @param terms The pushed-down keys' values per join field, or empty for none.
    record Chunk(KeyRange range, List<List<Object>> terms) {

        /// Copies the list.
        Chunk {
            terms = List.copyOf(terms);
        }
    }
}
