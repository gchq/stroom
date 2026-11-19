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

package stroom.ai.impl.tools.catalogue;

import stroom.ai.impl.tools.catalogue.CatalogueCheckResult.TellingField;
import stroom.ai.impl.tools.sample.KindFields;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/// The fields whose values tell two event types' records apart, for an overlap between them: so the agent has a
/// concrete way to tighten one (against thrash on weaker models). Read from each type's records the other doesn't
/// recognise (all its records, where every one is shared): a field whose values in the one never occur in the other,
/// or that one type's records all have and the other's never do.
final class TellingFields {

    // The most fields given an overlap, and values a field.
    static final int FIELDS = 3;
    static final int VALUES = 3;
    // The most records of each side read.
    private static final int READ = 500;
    // A value longer than this is free text, not a value to tell records by.
    private static final int LONGEST = 200;

    private TellingFields() {
        // Static only.
    }

    /// @param index    The records.
    /// @param aId      The first type's TypeId.
    /// @param a        Its records.
    /// @param bId      The second type's TypeId.
    /// @param b        Its records.
    /// @param excluded Fields never given (the source type field's spellings).
    /// @return The telling fields, best first.
    static List<TellingField> between(final RecordIndex index,
                                      final String aId,
                                      final BitSet a,
                                      final String bId,
                                      final BitSet b,
                                      final Predicate<String> excluded) {
        final Side one = side(index, others(a, b));
        final Side two = side(index, others(b, a));
        final List<Found> found = new ArrayList<>();
        final Set<String> fields = new LinkedHashSet<>(one.values().keySet());
        fields.addAll(two.values().keySet());
        for (final String field : fields) {
            if (excluded.test(field) || KindFields.namedAsTime(field) || KindFields.namedAsHandledWhere(field)) {
                continue;
            }
            final Field inOne = one.values().get(field);
            final Field inTwo = two.values().get(field);
            final Found told = told(field, inOne, inTwo, one.records(), two.records());
            if (told != null) {
                found.add(told);
            }
        }
        found.sort(Comparator.comparing(Found::byPresence)
                .thenComparing(candidate -> !KindFields.namedLikeKinds(candidate.field()))
                .thenComparingInt(Found::distinct)
                .thenComparingDouble(Found::length));
        final List<TellingField> out = new ArrayList<>();
        for (final Found told : found.subList(0, Math.min(FIELDS, found.size()))) {
            final Map<String, List<String>> values = new LinkedHashMap<>();
            values.put(aId, shown(one.values().get(told.field())));
            values.put(bId, shown(two.values().get(told.field())));
            out.add(new TellingField(told.field(), values));
        }
        return out;
    }

    // A type's records the other doesn't recognise; all of them where every one is shared.
    private static BitSet others(final BitSet own, final BitSet other) {
        final BitSet out = (BitSet) own.clone();
        out.andNot(other);
        return out.isEmpty()
                ? (BitSet) own.clone()
                : out;
    }

    private static Side side(final RecordIndex index, final BitSet records) {
        final Map<String, Field> values = new LinkedHashMap<>();
        int read = 0;
        for (int n = records.nextSetBit(0); n >= 0 && read < READ; n = records.nextSetBit(n + 1)) {
            read++;
            for (final String field : index.flat(n).keySet()) {
                final String text = index.text(n, field);
                if (text != null && !text.isBlank()) {
                    final Field held = values.computeIfAbsent(field, name -> new Field(new LinkedHashSet<>(),
                            new int[1], new long[1]));
                    held.records()[0]++;
                    if (held.values().size() <= READ) {
                        held.values().add(text.strip());
                    }
                    held.length()[0] += text.length();
                }
            }
        }
        return new Side(read, values);
    }

    // How a field tells the two sides apart, or null when it doesn't: values in each, never shared (and not each
    // record's own, as an id is), or in every record of one and none of the other.
    private static Found told(final String field,
                              final Field one,
                              final Field two,
                              final int oneRecords,
                              final int twoRecords) {
        if (one == null || two == null) {
            final Field had = one != null
                    ? one
                    : two;
            final int records = one != null
                    ? oneRecords
                    : twoRecords;
            return had.records()[0] == records
                    ? new Found(field, true, had.values().size(), had.averageLength())
                    : null;
        }
        if (one.records()[0] != oneRecords || two.records()[0] != twoRecords) {
            return null;
        }
        if (one.averageLength() > LONGEST || two.averageLength() > LONGEST) {
            return null;
        }
        if (ownEach(one, oneRecords) && ownEach(two, twoRecords)) {
            return null;
        }
        for (final String value : one.values()) {
            if (two.values().contains(value)) {
                return null;
            }
        }
        return new Found(field, false, one.values().size() + two.values().size(),
                (one.averageLength() + two.averageLength()) / 2);
    }

    // Whether a field's values are each record's own (an id): as many as the records, of more than two records.
    private static boolean ownEach(final Field field, final int records) {
        return records > 2 && field.values().size() >= records;
    }

    private static List<String> shown(final Field field) {
        return field == null
                ? List.of()
                : field.values().stream().limit(VALUES).map(KindFields::shortExample).toList();
    }


    // --------------------------------------------------------------------------------


    // A side's records read, and each field's values in them.
    private record Side(int records, Map<String, Field> values) {

    }


    // --------------------------------------------------------------------------------


    // A field's distinct values, how many records have it, and their values' length in all.
    private record Field(Set<String> values, int[] records, long[] length) {

        private double averageLength() {
            return records[0] == 0
                    ? 0
                    : (double) length[0] / records[0];
        }
    }


    // --------------------------------------------------------------------------------


    // A telling field: by its presence in one side only, or by its values; how many values, and how long.
    private record Found(String field, boolean byPresence, int distinct, double length) {

    }
}
