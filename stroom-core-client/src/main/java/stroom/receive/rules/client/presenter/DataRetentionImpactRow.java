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

package stroom.receive.rules.client.presenter;

import stroom.data.retention.shared.DataRetentionDeleteSummary;
import stroom.data.retention.shared.DataRetentionRule;
import stroom.data.retention.shared.FindDataRetentionImpactCriteria;
import stroom.util.shared.CompareUtil;
import stroom.util.shared.CriteriaFieldSort;
import stroom.util.shared.Expander;
import stroom.util.shared.NullSafe;
import stroom.util.shared.time.TimeUnit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class DataRetentionImpactRow {

    public static final String FIELD_NAME_RULE_NO = "Rule No.";
    public static final String FIELD_NAME_RULE_NAME = "Rule Name";
    public static final String FIELD_NAME_RULE_AGE = "Rule Age";
    public static final String FIELD_NAME_FEED = "Feed";
    public static final String FIELD_NAME_TYPE = "Type";
    public static final String FIELD_NAME_DELETE_COUNT = "Stream Delete Count";

    public static final Comparator<DataRetentionImpactRow> FEED_COMPARATOR = CompareUtil.getNullSafeComparator(
            DataRetentionImpactRow::getFeed);
    public static final Comparator<DataRetentionImpactRow> TYPE_COMPARATOR = CompareUtil.getNullSafeComparator(
            DataRetentionImpactRow::getType);
    public static final Comparator<DataRetentionImpactRow> COUNT_COMPARATOR = Comparator.comparingInt(
            DataRetentionImpactRow::getCount);
    public static final Comparator<DataRetentionImpactRow> RULE_NO_COMPARATOR = CompareUtil.getNullSafeComparator(
            DataRetentionImpactRow::getRuleNumber);
    public static final Comparator<DataRetentionImpactRow> RULE_NAME_COMPARATOR = CompareUtil.getNullSafeComparator(
            DataRetentionImpactRow::getRuleName);
    public static final Comparator<DataRetentionImpactRow> RULE_AGE_COMPARATOR =
            Comparator.comparingLong(row ->
                    timeUnitToMillis(row.ruleAge, row.timeUnit));

    public static final Map<String, Comparator<DataRetentionImpactRow>> FIELD_TO_COMPARATOR_MAP = new HashMap<>();

    // GWT doesn't like Map.of()
    static {
        FIELD_TO_COMPARATOR_MAP.put(FIELD_NAME_RULE_NO, RULE_NO_COMPARATOR);
        FIELD_TO_COMPARATOR_MAP.put(FIELD_NAME_RULE_NAME, RULE_NAME_COMPARATOR);
        FIELD_TO_COMPARATOR_MAP.put(FIELD_NAME_RULE_AGE, RULE_AGE_COMPARATOR);
        FIELD_TO_COMPARATOR_MAP.put(FIELD_NAME_FEED, FEED_COMPARATOR);
        FIELD_TO_COMPARATOR_MAP.put(FIELD_NAME_TYPE, TYPE_COMPARATOR);
        FIELD_TO_COMPARATOR_MAP.put(FIELD_NAME_DELETE_COUNT, COUNT_COMPARATOR);
    }

    private static final long MINUTE_MS = 60 * 1_000;
    private static final long HOUR_MS = 60 * MINUTE_MS;
    private static final long DAY_MS = 24 * HOUR_MS;
    private static final long WEEK_MS = 7 * DAY_MS;
    private static final long AVG_MONTH_MS = 365 / 12 * DAY_MS; // Approx, only for sorting
    private static final long YEAR_MS = 365 * DAY_MS;

    // Only to ensure uniqueness between rows that hold similar data
    private final String rowKey;
    private final Integer ruleNumber;
    private final String ruleName;
    private final String ruleAgeStr;
    private final int ruleAge;
    private final TimeUnit timeUnit;
    private final String feed;
    private final String type;
    private final int count;
    private Expander expander;

    public DataRetentionImpactRow(final String rowKey,
                                  final Integer ruleNumber,
                                  final String ruleName,
                                  final String ruleAgeStr,
                                  final int ruleAge,
                                  final TimeUnit timeUnit,
                                  final String feed,
                                  final String type,
                                  final int count) {
        this.rowKey = rowKey;
        this.ruleNumber = ruleNumber;
        this.ruleName = ruleName;
        this.ruleAgeStr = ruleAgeStr;
        this.ruleAge = ruleAge;
        this.timeUnit = timeUnit;
        this.feed = feed;
        this.type = type;
        this.count = count;
        this.expander = null;
    }

    private static String buildRowKey(final int ruleNumber,
                                      final String metaType,
                                      final String feedName) {
        return ruleNumber + "|" + metaType + "|" + feedName;
    }

    private static String buildRowKey(final DataRetentionDeleteSummary summary) {
        return buildRowKey(summary.getRuleNumber(), summary.getType(), summary.getFeed());
    }

    public Integer getRuleNumber() {
        return ruleNumber;
    }

    public String getRuleName() {
        return ruleName;
    }

    public String getRuleAgeStr() {
        return ruleAgeStr;
    }

    public String getFeed() {
        return feed;
    }

    public String getType() {
        return type;
    }

    public int getCount() {
        return count;
    }

    public Expander getExpander() {
        return expander;
    }

    public void setExpander(final Expander expander) {
        this.expander = expander;
    }

    private static Comparator<DataRetentionImpactRow> buildComparator(final FindDataRetentionImpactCriteria criteria) {

        final List<CriteriaFieldSort> sortList = criteria.getSortList();
        if (NullSafe.hasItems(sortList)) {
            //noinspection SimplifyStreamApiCallChains // Cos GWT
            final List<Comparator<DataRetentionImpactRow>> comparators = sortList.stream()
                    .filter(Objects::nonNull)
                    .map(sort ->
                            Optional.ofNullable(FIELD_TO_COMPARATOR_MAP.get(sort.getId()))
                                    .map(comparator -> {
                                        if (sort.isDesc()) {
                                            return comparator.reversed();
                                        } else {
                                            return comparator;
                                        }
                                    }))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .collect(Collectors.toList());

            return (o1, o2) -> {
                int result;
                for (final Comparator<DataRetentionImpactRow> comparator : comparators) {
                    if ((result = comparator.compare(o1, o2)) != 0) {
                        return result;
                    }
                }
                return 0;
            };
        } else {
            // No sort
            return (o1, o2) -> 0;
        }
    }

    public static List<DataRetentionImpactRow> buildFlatTable(final List<DataRetentionRule> rules,
                                                              final List<DataRetentionDeleteSummary> summaries,
                                                              final FindDataRetentionImpactCriteria criteria) {

        final Map<Integer, DataRetentionRule> ruleNoToRuleMap = rules.stream()
                .collect(Collectors.toMap(
                        DataRetentionRule::getRuleNumber, // Manual boxing to keep GWT happy
                        Function.identity()));

        return summaries.stream()
                .map(summary -> {
                    final DataRetentionRule rule = ruleNoToRuleMap.get(summary.getRuleNumber());
                    final int ruleNumber = summary.getRuleNumber();

                    final String rowKey = buildRowKey(ruleNumber, summary.getType(), summary.getFeed());

                    return new DataRetentionImpactRow(
                            rowKey,
                            ruleNumber,
                            summary.getRuleName(),
                            rule.getAgeString(),
                            rule.getAge(),
                            rule.getTimeUnit(),
                            summary.getFeed(),
                            summary.getType(),
                            summary.getCount());
                })
                .sorted(buildComparator(criteria))
                .collect(Collectors.toList());
    }

    public static List<DataRetentionImpactRow> buildNestedTable(final List<DataRetentionRule> rules,
                                                                final List<DataRetentionDeleteSummary> summaries,
                                                                final DataRetentionImpactTreeAction treeAction,
                                                                final FindDataRetentionImpactCriteria criteria) {
        final List<DataRetentionImpactRow> rows = new ArrayList<>();

        // Group our data by rule No.
        final Map<Integer, Set<DataRetentionDeleteSummary>> ruleNoToSummariesMap = summaries.stream()
                .collect(Collectors.groupingBy(
                        DataRetentionDeleteSummary::getRuleNumber,
                        Collectors.toSet()));

        rules.stream()
                .map(rule ->
                        buildRuleRow(
                                rule,
                                treeAction,
                                ruleNoToSummariesMap.get(rule.getRuleNumber())))
                .sorted(getComparator(
                        criteria,
                        RULE_NO_COMPARATOR,
                        FIELD_NAME_RULE_NO,
                        FIELD_NAME_RULE_NAME,
                        FIELD_NAME_RULE_AGE,
                        FIELD_NAME_DELETE_COUNT))
                .forEach(ruleRow -> {
                    final Set<DataRetentionDeleteSummary> summariesForRule = ruleNoToSummariesMap.get(
                            ruleRow.getRuleNumber());
                    rows.add(ruleRow);

                    if (isExpanded(treeAction, ruleRow, 0) && summariesForRule != null) {
                        // We do the sorting client side as the amount of data we are dealing with is
                        // probably relatively small (in the 1000s), and the DB query is potentially VERY slow

                        // Sub-group the data by meta type
                        final Map<String, Set<DataRetentionDeleteSummary>> summariesByRuleAndType =
                                summariesForRule.stream()
                                        .collect(Collectors.groupingBy(
                                                DataRetentionDeleteSummary::getType,
                                                Collectors.toSet()));

                        summariesByRuleAndType.keySet().stream()
                                .map(metaType ->
                                        buildMetaTypeRow(
                                                ruleRow.getRuleNumber(),
                                                metaType,
                                                summariesByRuleAndType.get(metaType),
                                                treeAction))
                                .sorted(getComparator(
                                        criteria,
                                        TYPE_COMPARATOR,
                                        FIELD_NAME_TYPE,
                                        FIELD_NAME_DELETE_COUNT))
                                .forEach(metaTypeRow -> {
                                    rows.add(metaTypeRow);

                                    final Set<DataRetentionDeleteSummary> summariesForRuleAndType =
                                            summariesByRuleAndType.get(metaTypeRow.getType());

                                    if (isExpanded(treeAction, metaTypeRow, 1)
                                        && summariesForRuleAndType != null) {

                                        final Comparator<DataRetentionImpactRow> feedRowComparator = getComparator(
                                                criteria,
                                                FEED_COMPARATOR,
                                                FIELD_NAME_FEED,
                                                FIELD_NAME_DELETE_COUNT);

                                        //noinspection SimplifyStreamApiCallChains // Cos GWT
                                        rows.addAll(summariesForRuleAndType.stream()
                                                .map(summaryForRuleTypeAndFeed ->
                                                        buildFeedRow(
                                                                summaryForRuleTypeAndFeed,
                                                                treeAction))
                                                .sorted(feedRowComparator)
                                                .collect(Collectors.toList()));
                                    }
                                });
                    }
                });

        return rows;
    }

    private static Comparator<DataRetentionImpactRow> getComparator(
            final FindDataRetentionImpactCriteria criteria,
            final Comparator<DataRetentionImpactRow> fallbackComparator,
            final String... sortableFieldNames) {

        // If the sort list contains any of the sortableFieldNames then create a
        // comparator for it. Assumes only one sort in the sort list

        final List<CriteriaFieldSort> sortList = criteria.getSortList();
        if (NullSafe.hasItems(sortList)) {
            return sortList.stream()
                    .filter(sort ->
                            Arrays.stream(sortableFieldNames)
                                    .anyMatch(fieldName ->
                                            fieldName.equals(sort.getId())))
                    .findAny()
                    .map(sort -> {
                        final Comparator<DataRetentionImpactRow> comparator = FIELD_TO_COMPARATOR_MAP.get(sort.getId());
                        return sort.isDesc()
                                ? comparator.reversed()
                                : comparator;
                    })
                    .orElse(fallbackComparator);
        } else {
            return fallbackComparator;
        }
    }

    private static DataRetentionImpactRow buildRuleRow(final DataRetentionRule rule,
                                                       final DataRetentionImpactTreeAction treeAction,
                                                       final Set<DataRetentionDeleteSummary> summaries) {
        final int totalCount = NullSafe.stream(summaries)
                .mapToInt(DataRetentionDeleteSummary::getCount)
                .sum();

        // TODO once we have built all the rows we will still have all the source data in memory.
        //   We could change this class so it holds a ref to the source summary object and then
        //   a number of functions to get the cell values from that source summary.  We would then
        //   just need three template rows for rule, type, feed levels.  This the impact row
        //   becomes a kind of view (with some mapping) onto the source row.

        // Set forever rules to 1000 years for sorting
        final DataRetentionImpactRow row = new DataRetentionImpactRow(
                buildRowKey(rule.getRuleNumber(), null, null),
                rule.getRuleNumber(),
                rule.getName(),
                rule.getAgeString(),
                rule.isForever()
                        ? 1000
                        : rule.getAge(),
                rule.isForever()
                        ? TimeUnit.YEARS
                        : rule.getTimeUnit(),
                null,
                null,
                totalCount);

        final int depth = 0;
        final boolean isLeaf = NullSafe.isEmptyCollection(summaries);

        setExpander(treeAction, row, depth, isLeaf);

        return row;
    }


    private static DataRetentionImpactRow buildMetaTypeRow(final int ruleNumber,
                                                           final String metaType,
                                                           final Set<DataRetentionDeleteSummary> summaries,
                                                           final DataRetentionImpactTreeAction treeAction) {

        final int countByRuleAndFeed = NullSafe.stream(summaries)
                .mapToInt(DataRetentionDeleteSummary::getCount)
                .sum();

        final DataRetentionImpactRow row = new DataRetentionImpactRow(
                buildRowKey(ruleNumber, metaType, null),
                null,
                null,
                null,
                0,
                null,
                null,
                metaType,
                countByRuleAndFeed);

        final int depth = 1;
        final boolean isLeaf = NullSafe.isEmptyCollection(summaries);

        setExpander(treeAction, row, depth, isLeaf);

        return row;
    }

    private static DataRetentionImpactRow buildFeedRow(final DataRetentionDeleteSummary summary,
                                                       final DataRetentionImpactTreeAction treeAction) {

        final DataRetentionImpactRow row = new DataRetentionImpactRow(
                buildRowKey(summary),
                null,
                null,
                null,
                0,
                null,
                summary.getFeed(),
                null,
                summary.getCount());

        final int depth = 2;
        final boolean isLeaf = true;

        setExpander(treeAction, row, depth, isLeaf);

        return row;
    }

    private static void setExpander(final DataRetentionImpactTreeAction treeAction,
                                    final DataRetentionImpactRow row,
                                    final int depth,
                                    final boolean isLeaf) {

        final boolean isExpanded = isExpanded(treeAction, row, depth);

        if (row.getExpander() == null) {
            row.setExpander(new Expander(depth, isExpanded, isLeaf));
        } else {
            row.getExpander().setExpanded(isExpanded);
        }

        if (!isLeaf) {
            treeAction.setRowExpanded(row, isExpanded);
        }
    }

    private static boolean isExpanded(final DataRetentionImpactTreeAction treeAction,
                                      final DataRetentionImpactRow row,
                                      final int depth) {
        // expanded if explicitly set or default to expanded if not set
        final boolean isExpanded = treeAction.isRowExpanded(row);
        final boolean isCollapsed = treeAction.isRowCollapsed(row);
        if (!isExpanded && !isCollapsed) {
            // State not known so default to collapsed for all but root level
            return depth <= 0;
        } else {
            return isExpanded;
        }
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DataRetentionImpactRow that = (DataRetentionImpactRow) o;
        return count == that.count &&
               Objects.equals(rowKey, that.rowKey) &&
               Objects.equals(ruleNumber, that.ruleNumber) &&
               Objects.equals(ruleName, that.ruleName) &&
               Objects.equals(ruleAgeStr, that.ruleAgeStr) &&
               Objects.equals(feed, that.feed) &&
               Objects.equals(type, that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(rowKey, ruleNumber, ruleName, ruleAgeStr, feed, type, count);
    }

    @Override
    public String toString() {
        return "DataRetentionImpactRow{" +
               "rowKey=" + rowKey +
               ", ruleNumber=" + ruleNumber +
               ", ruleName='" + ruleName + '\'' +
               ", ruleAge='" + ruleAgeStr + '\'' +
               ", feedName='" + feed + '\'' +
               ", metaType='" + type + '\'' +
               ", count=" + count +
               ", expander=" + expander +
               '}';
    }

    /**
     * Months/years are variable things so this is only approximate
     * for use in sorting
     */
    private static long timeUnitToMillis(final int ruleAge, final TimeUnit timeUnit) {
        final long unitMs;
        switch (timeUnit) {
            case MINUTES:
                unitMs = MINUTE_MS;
                break;
            case HOURS:
                unitMs = HOUR_MS;
                break;
            case DAYS:
                unitMs = DAY_MS;
                break;
            case WEEKS:
                unitMs = WEEK_MS;
                break;
            case MONTHS:
                unitMs = AVG_MONTH_MS;
                break;
            case YEARS:
                unitMs = YEAR_MS;
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + timeUnit);
        }
        return unitMs * ruleAge;
    }
}
