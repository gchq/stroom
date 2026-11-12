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

package stroom.query.common.v2;

import stroom.query.api.JoinType;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ref.ErrorConsumer;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.Severity;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/// Applies one join line to the rows it is joined to, looking each row's key up in the join table.
///
/// - `join` passes on one row per match, with the joined fields added, and drops rows with no match.
/// - `left join` does the same, but passes on rows with no match with blank joined fields.
/// - `keep join` passes on each row with at least one match, once, as it is.
/// - `drop join` passes on each row with no match, as it is.
///
/// Rows are never changed in place; a row with joined fields added is a copy.
public final class JoinProbe {

    /// A row that matches more rows than this gets a warning, as the result may be far bigger than
    /// expected.
    static final int MATCH_WARNING_COUNT = 1_000;

    private final JoinPlan plan;
    private final JoinTable table;
    private final JoinType type;
    // The position in the rows of each field the table holds, or -1 where the query doesn't need it.
    private final int[] targetPositions;
    private final int rowLength;
    private final ErrorConsumer errorConsumer;
    private final AtomicBoolean warnedOfManyMatches = new AtomicBoolean();

    /// Creates a probe for one join line.
    ///
    /// @param plan          The join line's plan.
    /// @param table         The join line's complete table.
    /// @param fieldIndex    The field index of the rows the join is applied to, holding every field.
    /// @param errorConsumer Takes the warning about rows with very many matches.
    public JoinProbe(final JoinPlan plan,
                     final JoinTable table,
                     final FieldIndex fieldIndex,
                     final ErrorConsumer errorConsumer) {
        this.plan = plan;
        this.table = table;
        this.type = plan.getJoin().getType();
        this.errorConsumer = errorConsumer;
        final String alias = plan.getJoin().getAlias();
        final List<String> storedFields = plan.getStoredFields();
        this.targetPositions = new int[storedFields.size()];
        for (int i = 0; i < storedFields.size(); i++) {
            final Integer pos = fieldIndex.getPos(alias + "." + storedFields.get(i));
            targetPositions[i] = pos == null
                    ? -1
                    : pos;
        }
        this.rowLength = fieldIndex.size();
    }

    /// Applies the join line to a row.
    ///
    /// @param row      The row, at the positions of the field index the probe was created with.
    /// @param consumer Takes each row the join passes on.
    public void probe(final Val[] row, final Consumer<Val[]> consumer) {
        final String key = plan.getLeftKeys().getKey(row);
        switch (type) {
            case INNER -> addMatches(row, key, consumer);
            case LEFT -> {
                if (addMatches(row, key, consumer) == 0) {
                    consumer.accept(row);
                }
            }
            case KEEP -> {
                if (table.hasMatch(key)) {
                    consumer.accept(row);
                }
            }
            case DROP -> {
                if (!table.hasMatch(key)) {
                    consumer.accept(row);
                }
            }
        }
    }

    // Passes on a copy of the row for each match, with the match's fields added.
    private int addMatches(final Val[] row, final String key, final Consumer<Val[]> consumer) {
        final int matchCount = table.forEachMatch(key, match -> {
            final Val[] joined = Arrays.copyOf(row, Math.max(row.length, rowLength));
            for (int i = 0; i < targetPositions.length && i < match.length; i++) {
                final int pos = targetPositions[i];
                if (pos >= 0) {
                    joined[pos] = match[i];
                }
            }
            consumer.accept(joined);
        });
        if (matchCount > MATCH_WARNING_COUNT && warnedOfManyMatches.compareAndSet(false, true)) {
            errorConsumer.add(Severity.WARNING, () -> "A row matched " + ModelStringUtil.formatCsv(matchCount)
                                                      + " rows of '" + plan.getJoin().getAlias()
                                                      + "', so the result may be much bigger than expected. "
                                                      + "Check the keys of the join.");
        }
        return matchCount;
    }
}
