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

import stroom.query.api.JoinKey;
import stroom.query.api.JoinType;
import stroom.query.api.JoinWindow;
import stroom.query.api.Query;
import stroom.query.api.QueryJoin;
import stroom.query.language.JoinWhereSplitter;
import stroom.query.language.functions.ExpressionContext;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValLong;
import stroom.query.language.functions.ref.ErrorConsumer;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.Severity;
import stroom.util.shared.time.SimpleDuration;
import stroom.util.time.SimpleDurationUtil;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/// The plan of an event-to-event join, as in
/// `join "File events" as export on logon.UserId = export.UserId within 10m after logon.EventTime`.
///
/// Both sides are events, so the first source is the anchor: its rows are held in a join table, keyed
/// on the left keys, with the time each window starts or ends at. The joined source is then searched
/// for the anchors' time span, widened by the window, and each of its rows is joined to every anchor
/// with the same key whose window holds the row's time. The joined source's time is the time field of
/// its data source.
///
/// - `join` gives one row per anchor and joined row in its window.
/// - `left join` does the same, then gives each anchor with no joined row in its window, with blank
///   joined fields.
/// - `keep join` gives each anchor with at least one joined row in its window, once.
/// - `drop join` gives each anchor with no joined row in its window.
public final class WindowedJoin {

    // The field of an anchor row that holds its number, for knowing which anchors matched.
    private static final String ANCHOR_NUMBER_FIELD = "__anchor_number__";

    private final QueryJoin join;
    private final SimpleDuration duration;
    private final boolean after;
    // The fields of an anchor row, named as the query names them, bare or with the first source's
    // alias, then the anchor's number.
    private final FieldIndex leftFieldIndex;
    private final List<String> leftSourceFields;
    private final JoinKeys leftKeys;
    private final int leftTimePos;
    // The fields of a joined row, named with the joined source's alias.
    private final FieldIndex rightFieldIndex;
    private final List<String> rightSourceFields;
    private final JoinKeys rightKeys;
    private final int rightTimePos;

    private WindowedJoin(final QueryJoin join,
                         final SimpleDuration duration,
                         final FieldIndex leftFieldIndex,
                         final List<String> leftSourceFields,
                         final JoinKeys leftKeys,
                         final FieldIndex rightFieldIndex,
                         final List<String> rightSourceFields,
                         final JoinKeys rightKeys,
                         final String rightTimeField) {
        this.join = join;
        this.duration = duration;
        this.after = join.getWindow().isAfter();
        this.leftFieldIndex = leftFieldIndex;
        this.leftSourceFields = leftSourceFields;
        this.leftKeys = leftKeys;
        this.leftTimePos = leftFieldIndex.getPos(join.getWindow().getTimeField());
        this.rightFieldIndex = rightFieldIndex;
        this.rightSourceFields = rightSourceFields;
        this.rightKeys = rightKeys;
        this.rightTimePos = rightFieldIndex.getPos(join.getAlias() + "." + rightTimeField);
    }

    /// Finds whether a query has an event-to-event join.
    ///
    /// @param query The query.
    /// @return True if any of its join lines has a time window.
    public static boolean isWindowed(final Query query) {
        return NullSafe.list(query.getJoins()).stream().anyMatch(join -> join.getWindow() != null);
    }

    /// Plans a query's event-to-event join.
    ///
    /// @param query             The query, whose only join line has a time window.
    /// @param fieldIndex        The fields the query names, as its coprocessors' field index holds them.
    /// @param expressionContext The query's expression context.
    /// @param paramMap          The query's parameters.
    /// @param rightTimeField    The time field of the joined source's data source.
    /// @return The plan.
    /// @throws IllegalArgumentException If the query's join can't be run as an event-to-event join.
    public static WindowedJoin create(final Query query,
                                      final FieldIndex fieldIndex,
                                      final ExpressionContext expressionContext,
                                      final Map<String, String> paramMap,
                                      final String rightTimeField) {
        final List<QueryJoin> joins = NullSafe.list(query.getJoins());
        if (joins.size() != 1) {
            throw new IllegalArgumentException("A query with a join that has a time window, as in 'within 10m "
                                               + "after', can't have other join lines yet");
        }
        if (query.getSourceTableSettings() != null) {
            throw new IllegalArgumentException("A join with a time window can't follow a bracketed query in "
                                               + "'from' yet");
        }
        final QueryJoin join = joins.getFirst();
        if (join.getTableSettings() != null) {
            throw new IllegalArgumentException("A join with a time window must join a data source, not a "
                                               + "bracketed query");
        }
        final SimpleDuration duration = parseDuration(join.getWindow());
        final String alias = join.getAlias();
        final List<String> aliases = JoinPlan.getAliases(query);

        // The anchors hold every first source field the query names, those the left keys name and the
        // window's time field.
        final FieldIndex leftFieldIndex = new FieldIndex();
        for (final String field : fieldIndex.getFields()) {
            if (!alias.equals(JoinWhereSplitter.findAlias(field, aliases))) {
                leftFieldIndex.create(field);
            }
        }
        final JoinKeys leftKeys = JoinKeys.compile(
                expressionContext, join.getKeys().stream().map(JoinKey::getLeft).toList(), leftFieldIndex, paramMap);
        for (final String field : leftFieldIndex.getFields()) {
            if (alias.equals(JoinWhereSplitter.findAlias(field, aliases))) {
                throw new IllegalArgumentException("The first source's side of each key of a join with a time "
                                                   + "window can't name a field of '" + alias + "', as '"
                                                   + field + "' does");
            }
        }
        final String timeField = join.getWindow().getTimeField();
        if (alias.equals(JoinWhereSplitter.findAlias(timeField, aliases))) {
            throw new IllegalArgumentException("The time field of a join's window, '" + timeField
                                               + "', must be a field of the first source, not of '"
                                               + alias + "'");
        }
        leftFieldIndex.create(timeField);
        final List<String> leftSourceFields = new ArrayList<>();
        for (final String field : leftFieldIndex.getFields()) {
            leftSourceFields.add(getFirstSourceField(query.getAlias(), field));
        }
        leftFieldIndex.create(ANCHOR_NUMBER_FIELD);

        // The joined rows hold the fields a join's table would, and the time field of the joined
        // source's data source.
        final Set<String> rightFields = new LinkedHashSet<>(
                JoinPlan.getStoredFields(join, fieldIndex, aliases, expressionContext, paramMap));
        rightFields.add(rightTimeField);
        final FieldIndex rightFieldIndex = new FieldIndex();
        rightFields.forEach(field -> rightFieldIndex.create(alias + "." + field));
        final List<String> rightExpressions = join.getKeys().stream().map(JoinKey::getRight).toList();
        final JoinKeys rightKeys = JoinKeys.compile(expressionContext, rightExpressions, rightFieldIndex, paramMap);

        return new WindowedJoin(join, duration, leftFieldIndex, List.copyOf(leftSourceFields), leftKeys,
                rightFieldIndex, List.copyOf(rightFields), rightKeys, rightTimeField);
    }

    private static SimpleDuration parseDuration(final JoinWindow window) {
        try {
            return SimpleDuration.parse(window.getDuration());
        } catch (final RuntimeException e) {
            throw new IllegalArgumentException("The window of a join must be a duration such as 10m, not '"
                                               + window.getDuration() + "'", e);
        }
    }

    // A field named with the first source's alias, as in logon.UserId, is the first source's UserId.
    private static String getFirstSourceField(final String firstSourceAlias, final String field) {
        if (firstSourceAlias != null && field.startsWith(firstSourceAlias + ".")) {
            return field.substring(firstSourceAlias.length() + 1);
        }
        return field;
    }

    /// Gets the join line.
    ///
    /// @return The join line.
    public QueryJoin getJoin() {
        return join;
    }

    /// Gets the fields of an anchor row, as the query names them, without the anchor's number, for
    /// naming the columns of the first source's search.
    ///
    /// @return The field names.
    public List<String> getLeftFields() {
        final String[] fields = leftFieldIndex.getFields();
        return List.of(Arrays.copyOf(fields, fields.length - 1));
    }

    /// Gets the first source's field each anchor field is, in the order of [#getLeftFields()].
    ///
    /// @return The first source's field names, without its alias.
    public List<String> getLeftSourceFields() {
        return leftSourceFields;
    }

    /// Gets the fields an anchor table holds: those of [#getLeftFields()] and the anchor's number.
    ///
    /// @return The field names.
    public List<String> getAnchorTableFields() {
        return List.of(leftFieldIndex.getFields());
    }

    /// Gets the joined source's fields its search must give, without the alias.
    ///
    /// @return The field names.
    public List<String> getRightSourceFields() {
        return rightSourceFields;
    }

    /// Makes an anchor row from the values of the first source's search.
    ///
    /// @param values The values, in the order of [#getLeftFields()].
    /// @param number The anchor's number, counting from 0.
    /// @return The anchor row, as an anchor table holds it.
    public Val[] createAnchorRow(final Val[] values, final long number) {
        final Val[] row = Arrays.copyOf(values, leftFieldIndex.size());
        row[row.length - 1] = ValLong.create(number);
        return row;
    }

    /// Works out the key of an anchor row.
    ///
    /// @param anchorRow The anchor row.
    /// @return The key, or `null` if it has none.
    public String getLeftKey(final Val[] anchorRow) {
        return leftKeys.getKey(anchorRow);
    }

    /// Gets the time an anchor's window starts or ends at.
    ///
    /// @param anchorRow The anchor row.
    /// @return The time, or `null` if the anchor has none.
    public Instant getAnchorTime(final Val[] anchorRow) {
        return getTime(anchorRow, leftTimePos);
    }

    /// Gets the earliest time a joined row may have to fall in the window of an anchor at the given
    /// time.
    ///
    /// @param anchorTime The anchor's time.
    /// @return The start of its window.
    public Instant getWindowStart(final Instant anchorTime) {
        return after
                ? anchorTime
                : SimpleDurationUtil.minus(anchorTime, duration);
    }

    /// Gets the latest time a joined row may have to fall in the window of an anchor at the given time.
    ///
    /// @param anchorTime The anchor's time.
    /// @return The end of its window.
    public Instant getWindowEnd(final Instant anchorTime) {
        return after
                ? SimpleDurationUtil.plus(anchorTime, duration)
                : anchorTime;
    }

    /// Works out the time ranges to search the joined source over: the windows of the anchors, merged
    /// where they overlap, then, if there are too many, merged across the smallest gaps between them.
    /// Every window falls within a range, so no joined row in a window is missed.
    ///
    /// @param anchorTimes The times of the anchors.
    /// @param maxRanges   The most ranges to give, at least 1.
    /// @return The ranges, in time order, or none if there are no anchor times.
    public List<TimeSpan> getSearchRanges(final long[] anchorTimes, final int maxRanges) {
        final long[] times = anchorTimes.clone();
        Arrays.sort(times);
        final List<TimeSpan> merged = new ArrayList<>();
        for (final long time : times) {
            final Instant anchorTime = Instant.ofEpochMilli(time);
            final TimeSpan window = new TimeSpan(getWindowStart(anchorTime), getWindowEnd(anchorTime));
            if (!merged.isEmpty() && !window.from().isAfter(merged.getLast().to())) {
                final TimeSpan last = merged.removeLast();
                merged.add(new TimeSpan(last.from(), last.to().isAfter(window.to())
                        ? last.to()
                        : window.to()));
            } else {
                merged.add(window);
            }
        }
        if (merged.size() <= maxRanges) {
            return merged;
        }
        // Keep the largest gaps between ranges, and close the rest.
        final List<Integer> gaps = new ArrayList<>();
        for (int i = 1; i < merged.size(); i++) {
            gaps.add(i);
        }
        gaps.sort((a, b) -> Long.compare(gapMillis(merged, b), gapMillis(merged, a)));
        final List<Integer> splits = new ArrayList<>(gaps.subList(0, maxRanges - 1));
        splits.sort(Integer::compare);
        final List<TimeSpan> ranges = new ArrayList<>(maxRanges);
        int start = 0;
        for (final int split : splits) {
            ranges.add(new TimeSpan(merged.get(start).from(), merged.get(split - 1).to()));
            start = split;
        }
        ranges.add(new TimeSpan(merged.get(start).from(), merged.getLast().to()));
        return ranges;
    }

    // The time between a range and the one before it.
    private static long gapMillis(final List<TimeSpan> ranges, final int index) {
        return ranges.get(index).from().toEpochMilli() - ranges.get(index - 1).to().toEpochMilli();
    }

    /// Creates the probe that joins the joined source's rows to the anchors.
    ///
    /// @param anchors       The complete anchor table.
    /// @param anchorCount   The number of anchors in the table.
    /// @param fieldIndex    The field index of the joined rows to make, holding every field the query
    ///                      names.
    /// @param errorConsumer Takes the warning about rows with very many matches.
    /// @return The probe.
    public Probe createProbe(final JoinTable anchors,
                             final int anchorCount,
                             final FieldIndex fieldIndex,
                             final ErrorConsumer errorConsumer) {
        return new Probe(anchors, anchorCount, fieldIndex, errorConsumer);
    }

    private static Instant getTime(final Val[] row, final int pos) {
        if (pos >= row.length || row[pos] == null || !row[pos].type().isValue()) {
            return null;
        }
        final Long millis = row[pos].toLong();
        return millis == null
                ? null
                : Instant.ofEpochMilli(millis);
    }


    // --------------------------------------------------------------------------------


    /// Joins the joined source's rows to the anchors, then gives the anchors the kind of join gives
    /// once every joined row is done.
    public final class Probe {

        private final JoinTable anchors;
        private final BitSet matched;
        // The position in an anchor or joined row of each field of a joined row, or -1.
        private final int[] fromLeft;
        private final int[] fromRight;
        private final ErrorConsumer errorConsumer;
        private boolean warnedOfManyMatches;

        private Probe(final JoinTable anchors,
                      final int anchorCount,
                      final FieldIndex fieldIndex,
                      final ErrorConsumer errorConsumer) {
            this.anchors = anchors;
            this.matched = new BitSet(anchorCount);
            this.errorConsumer = errorConsumer;
            fromLeft = new int[fieldIndex.size()];
            fromRight = new int[fieldIndex.size()];
            for (int i = 0; i < fieldIndex.size(); i++) {
                // A field the joined rows hold is named with the joined source's alias; any other is
                // the first source's, which the anchors hold.
                final String field = fieldIndex.getField(i);
                fromRight[i] = getPos(rightFieldIndex, field);
                fromLeft[i] = fromRight[i] >= 0
                        ? -1
                        : getPos(leftFieldIndex, field);
            }
        }

        private static int getPos(final FieldIndex fieldIndex, final String field) {
            final Integer pos = fieldIndex.getPos(field);
            return pos == null
                    ? -1
                    : pos;
        }

        /// Joins a row of the joined source to every anchor with its key whose window holds its time.
        /// Inner and left joins give a joined row for each.
        ///
        /// @param values   The joined row's values, in the order of [#getRightSourceFields()].
        /// @param consumer Takes each joined row.
        public void probe(final Val[] values, final Consumer<Val[]> consumer) {
            final String key = rightKeys.getKey(values);
            final Instant time = getTime(values, rightTimePos);
            if (key == null || time == null) {
                return;
            }
            final boolean addsFields = join.getType().addsFields();
            final int[] count = new int[1];
            anchors.forEachMatch(key, anchor -> {
                final Instant anchorTime = getAnchorTime(anchor);
                if (anchorTime != null
                    && !time.isBefore(getWindowStart(anchorTime))
                    && !time.isAfter(getWindowEnd(anchorTime))) {
                    matched.set(getAnchorNumber(anchor));
                    if (addsFields) {
                        count[0]++;
                        consumer.accept(createJoinedRow(anchor, values));
                    }
                }
            });
            if (count[0] > JoinProbe.MATCH_WARNING_COUNT && !warnedOfManyMatches) {
                warnedOfManyMatches = true;
                errorConsumer.add(Severity.WARNING, () -> "A row of '" + join.getAlias() + "' matched "
                                                          + ModelStringUtil.formatCsv(count[0])
                                                          + " rows, so the result may be much bigger than "
                                                          + "expected. Check the keys of the join.");
            }
        }

        /// Gives the anchors the kind of join gives once every joined row is done: unmatched ones for
        /// left and drop joins, with blank joined fields, and matched ones for keep joins.
        ///
        /// @param consumer Takes each row.
        public void finish(final Consumer<Val[]> consumer) {
            final JoinType type = join.getType();
            if (type == JoinType.INNER) {
                return;
            }
            final boolean wantMatched = type == JoinType.KEEP;
            anchors.forEachRow((key, anchor) -> {
                if (matched.get(getAnchorNumber(anchor)) == wantMatched) {
                    consumer.accept(createJoinedRow(anchor, null));
                }
            });
        }

        private int getAnchorNumber(final Val[] anchor) {
            return anchor[anchor.length - 1].toInteger();
        }

        private Val[] createJoinedRow(final Val[] anchor, final Val[] right) {
            final Val[] row = new Val[fromLeft.length];
            for (int i = 0; i < row.length; i++) {
                if (fromLeft[i] >= 0) {
                    row[i] = anchor[fromLeft[i]];
                } else if (right != null && fromRight[i] >= 0) {
                    row[i] = right[fromRight[i]];
                }
            }
            return row;
        }
    }


    // --------------------------------------------------------------------------------


    /// A span of time, both ends included.
    ///
    /// @param from The start.
    /// @param to   The end.
    public record TimeSpan(Instant from, Instant to) {

    }
}
