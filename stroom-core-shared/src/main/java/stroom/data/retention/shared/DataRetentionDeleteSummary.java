package stroom.data.retention.shared;

import java.util.Objects;

public class DataRetentionDeleteSummary {

    private final String feedName;
    private final String metaType;
    private final int ruleNumber;
    private final String ruleName;
    private final int timeTillDelete;
    private final TimeUnit timeTillDeleteUnits;

    public DataRetentionDeleteSummary(final String feedName,
                                      final String metaType,
                                      final int ruleNumber,
                                      final String ruleName,
                                      final int timeTillDelete,
                                      final TimeUnit timeTillDeleteUnits) {
        this.feedName = Objects.requireNonNull(feedName);
        this.metaType = Objects.requireNonNull(metaType);
        this.ruleNumber = ruleNumber;
        this.ruleName = Objects.requireNonNull(ruleName);
        this.timeTillDelete = timeTillDelete;
        this.timeTillDeleteUnits = Objects.requireNonNull(timeTillDeleteUnits);
    }

    public String getFeedName() {
        return feedName;
    }

    public String getMetaType() {
        return metaType;
    }

    public int getRuleNumber() {
        return ruleNumber;
    }

    public String getRuleName() {
        return ruleName;
    }

    public int getTimeTillDelete() {
        return timeTillDelete;
    }

    public TimeUnit getTimeTillDeleteUnits() {
        return timeTillDeleteUnits;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final DataRetentionDeleteSummary that = (DataRetentionDeleteSummary) o;
        return ruleNumber == that.ruleNumber &&
                timeTillDelete == that.timeTillDelete &&
                feedName.equals(that.feedName) &&
                metaType.equals(that.metaType) &&
                ruleName.equals(that.ruleName) &&
                timeTillDeleteUnits == that.timeTillDeleteUnits;
    }

    @Override
    public int hashCode() {
        return Objects.hash(feedName, metaType, ruleNumber, ruleName, timeTillDelete, timeTillDeleteUnits);
    }

    @Override
    public String toString() {
        return "DataRetentionDeleteSummary{" +
                "feedName='" + feedName + '\'' +
                ", metaType='" + metaType + '\'' +
                ", ruleNumber=" + ruleNumber +
                ", ruleName='" + ruleName + '\'' +
                ", timeTillDelete=" + timeTillDelete +
                ", timeTillDeleteUnits=" + timeTillDeleteUnits +
                '}';
    }
}
