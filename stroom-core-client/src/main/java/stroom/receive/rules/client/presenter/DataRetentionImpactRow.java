package stroom.receive.rules.client.presenter;

import stroom.data.retention.shared.DataRetentionDeleteSummary;
import stroom.data.retention.shared.DataRetentionRule;
import stroom.util.shared.Expander;
import stroom.util.shared.Sort.Direction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class DataRetentionImpactRow {

    public static final String FIELD_NAME_RULE_NO = "Rule No.";
    public static final String FIELD_NAME_RULE_NAME = "Rule Name";
    public static final String FIELD_NAME_RULE_AGE = "Rule Age";
    public static final String FIELD_NAME_FEED_NAME = "Feed Name";
    public static final String FIELD_NAME_META_TYPE = "Meta Type";
    public static final String FIELD_NAME_DELETE_COUNT = "Delete Count";

    public static final Map<String, Comparator<DataRetentionImpactRow>> FIELD_TO_COMPARATOR_MAP = new HashMap<>();

    // GWT doesn't like Map.of()
    static {
        FIELD_TO_COMPARATOR_MAP.put(FIELD_NAME_FEED_NAME, Comparator.comparing(DataRetentionImpactRow::getFeedName));
        FIELD_TO_COMPARATOR_MAP.put(FIELD_NAME_META_TYPE, Comparator.comparing(DataRetentionImpactRow::getMetaType));
        FIELD_TO_COMPARATOR_MAP.put(FIELD_NAME_DELETE_COUNT, Comparator.comparing(DataRetentionImpactRow::getCount));
    }

    private Integer ruleNumber;
    private String ruleName;
    private String ruleAge;
    private String feedName;
    private String metaType;
    private int count;
    private Expander expander;

    public DataRetentionImpactRow(final Integer ruleNumber,
                                  final String ruleName,
                                  final String ruleAge,
                                  final String feedName,
                                  final String metaType,
                                  final int count,
                                  final Expander expander) {
        this.ruleNumber = ruleNumber;
        this.ruleName = ruleName;
        this.ruleAge = ruleAge;
        this.feedName = feedName;
        this.metaType = metaType;
        this.count = count;
        this.expander = expander;
    }

    public DataRetentionImpactRow(final Integer ruleNumber,
                                  final String ruleName,
                                  final String ruleAge,
                                  final String feedName,
                                  final String metaType,
                                  final int count) {
        this.ruleNumber = ruleNumber;
        this.ruleName = ruleName;
        this.ruleAge = ruleAge;
        this.feedName = feedName;
        this.metaType = metaType;
        this.count = count;
        this.expander = null;
    }

    public Integer getRuleNumber() {
        return ruleNumber;
    }

    public void setRuleNumber(final Integer ruleNumber) {
        this.ruleNumber = ruleNumber;
    }

    public String getRuleName() {
        return ruleName;
    }

    public void setRuleName(final String ruleName) {
        this.ruleName = ruleName;
    }

    public String getRuleAge() {
        return ruleAge;
    }

    public void setRuleAge(final String ruleAge) {
        this.ruleAge = ruleAge;
    }

    public String getFeedName() {
        return feedName;
    }

    public void setFeedName(final String feedName) {
        this.feedName = feedName;
    }

    public String getMetaType() {
        return metaType;
    }

    public void setMetaType(final String metaType) {
        this.metaType = metaType;
    }

    public int getCount() {
        return count;
    }

    public void setCount(final int count) {
        this.count = count;
    }

    public Expander getExpander() {
        return expander;
    }

    public void setExpander(final Expander expander) {
        this.expander = expander;
    }

    private Comparator<DataRetentionImpactRow> chainComparators(
            final Comparator<DataRetentionImpactRow> first,
            final Comparator<DataRetentionImpactRow> second) {

        if (first == null) {
            return second;
        } else {
            return first.thenComparing(second);
        }
    }

    private static Comparator<DataRetentionImpactRow> buildComparator(final FindDataRetentionImpactCriteria criteria) {

        if (criteria != null && criteria.getSortList() != null) {
            List<Comparator<DataRetentionImpactRow>> comparators = criteria.getSortList().stream()
                    .filter(Objects::nonNull)
                    .map(sort ->
                            Optional.ofNullable(FIELD_TO_COMPARATOR_MAP.get(sort.getField()))
                                    .map(comparator -> {
                                        if (Direction.DESCENDING.equals(sort.getDirection())) {
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
                for (Comparator<DataRetentionImpactRow> comparator : comparators) {
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

    public static List<DataRetentionImpactRow> buildTree(final List<DataRetentionRule> rules,
                                                         final List<DataRetentionDeleteSummary> summaries,
                                                         final DataRetentionImpactTreeAction treeAction,
                                                         final FindDataRetentionImpactCriteria criteria) {
        final List<DataRetentionImpactRow> rows = new ArrayList<>();

        final Map<Integer, Set<DataRetentionDeleteSummary>> ruleNoToSummariesMap = summaries.stream()
                .collect(Collectors.groupingBy(
                        DataRetentionDeleteSummary::getRuleNumber,
                        Collectors.toSet()));

        rules.forEach(rule -> {

            Set<DataRetentionDeleteSummary> summariesForRule = ruleNoToSummariesMap.get(rule.getRuleNumber());

            DataRetentionImpactRow ruleRow = buildRuleRow(rule, treeAction, summariesForRule);
            rows.add(ruleRow);

            if (isExpanded(treeAction, ruleRow)) {
//        // TODO column sorting
                if (summariesForRule != null) {
                    rows.addAll(summariesForRule
                            .stream()
//                            .sorted(Comparator.comparing(DataRetentionDeleteSummary::getFeedName)
//                                    .thenComparing(DataRetentionDeleteSummary::getMetaType))
                            .map(summary -> buildDetailRow(summary, treeAction))
                            .sorted(buildComparator(criteria))
                            .collect(Collectors.toList()));
                }
            }
        });

//        summaries.stream()
//                .collect(Collectors.toMap(
//                        DataRetentionDeleteSummary::getRuleNumber,
//                        Function.identity()))
//                .forEach((ruleNo, summariesForRule) -> {
//                    rows.add(new DataRetentionImpactRow())
//                })

        return rows;
    }

    private static DataRetentionImpactRow buildRuleRow(final DataRetentionRule rule,
                                                       final DataRetentionImpactTreeAction treeAction,
                                                       final Set<DataRetentionDeleteSummary> summaries) {
        final int totalCount;
        if (summaries != null) {
            totalCount = summaries.stream()
                    .mapToInt(DataRetentionDeleteSummary::getCount)
                    .sum();
        } else {
            totalCount = 0;
        }

        final DataRetentionImpactRow row = new DataRetentionImpactRow(
                rule.getRuleNumber(),
                rule.getName(),
                rule.getAgeString(),
                null,
                null,
                totalCount,
                null);

        final int depth = 0;
        final boolean isLeaf = summaries == null || summaries.isEmpty();

        setExpander(treeAction, row, depth, isLeaf);

        return row;
    }


    private static DataRetentionImpactRow buildDetailRow(final DataRetentionDeleteSummary summary,
                                                         final DataRetentionImpactTreeAction treeAction) {

        final DataRetentionImpactRow row = new DataRetentionImpactRow(
                null,
                null,
                null,
                summary.getFeedName(),
                summary.getMetaType(),
                summary.getCount(),
                null);

        final int depth = 1;
        final boolean isLeaf = true;

        setExpander(treeAction, row, depth, isLeaf);

        return row;
    }

    private static void setExpander(final DataRetentionImpactTreeAction treeAction,
                                    final DataRetentionImpactRow row,
                                    final int depth,
                                    final boolean isLeaf) {

        boolean isExpanded = isExpanded(treeAction, row);

        if (row.getExpander() == null) {
            row.setExpander(new Expander(depth, isExpanded, isLeaf));
        } else {
            row.getExpander().setExpanded(isExpanded);
        }

        treeAction.setRowExpanded(row, isExpanded);
    }

    private static boolean isExpanded(final DataRetentionImpactTreeAction treeAction,
                                      final DataRetentionImpactRow row) {
        // expanded if explicitly set or default to expanded if not set
        boolean isExpanded = treeAction.isRowExpanded(row);
        boolean isCollapsed = treeAction.isRowCollapsed(row);
        if (!isExpanded && !isCollapsed) {
            // State not known so default to collapsed
            return false;
        } else {
            return isExpanded;
        }
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final DataRetentionImpactRow that = (DataRetentionImpactRow) o;
        return count == that.count &&
                Objects.equals(ruleNumber, that.ruleNumber) &&
                Objects.equals(ruleName, that.ruleName) &&
                Objects.equals(ruleAge, that.ruleAge) &&
                Objects.equals(feedName, that.feedName) &&
                Objects.equals(metaType, that.metaType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ruleNumber, ruleName, ruleAge, feedName, metaType, count);
    }

    @Override
    public String toString() {
        return "DataRetentionImpactRow{" +
                "ruleNumber=" + ruleNumber +
                ", ruleName='" + ruleName + '\'' +
                ", ruleAge='" + ruleAge + '\'' +
                ", feedName='" + feedName + '\'' +
                ", metaType='" + metaType + '\'' +
                ", count=" + count +
                ", expander=" + expander +
                '}';
    }
}
