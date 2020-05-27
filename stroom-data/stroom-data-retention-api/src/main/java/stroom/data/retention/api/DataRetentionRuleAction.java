package stroom.data.retention.api;

import stroom.data.retention.shared.DataRetentionRule;

import java.util.Comparator;
import java.util.Objects;

public class DataRetentionRuleAction {
    private final DataRetentionRule dataRetentionRule;
    private final RetentionRuleOutcome retentionRuleOutcome;

    public DataRetentionRuleAction(final DataRetentionRule dataRetentionRule,
                                   final RetentionRuleOutcome retentionRuleOutcome) {
        this.dataRetentionRule = Objects.requireNonNull(dataRetentionRule);
        this.retentionRuleOutcome = Objects.requireNonNull(retentionRuleOutcome);
    }

    public DataRetentionRule getRule() {
        return dataRetentionRule;
    }

    public RetentionRuleOutcome getOutcome() {
        return retentionRuleOutcome;
    }

    public static Comparator<DataRetentionRuleAction> comparingByRuleNo() {
        return Comparator.comparingInt(dataRetentionRuleAction ->
                dataRetentionRuleAction.getRule().getRuleNumber());
    }

    @Override
    public String toString() {
        return "DataRetentionRuleAction{" +
                "retentionRuleOutcome=" + retentionRuleOutcome +
                ", dataRetentionRule=" + dataRetentionRule +
                '}';
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final DataRetentionRuleAction that = (DataRetentionRuleAction) o;
        return retentionRuleOutcome == that.retentionRuleOutcome &&
                dataRetentionRule.equals(that.dataRetentionRule);
    }

    @Override
    public int hashCode() {
        return Objects.hash(retentionRuleOutcome, dataRetentionRule);
    }
}
