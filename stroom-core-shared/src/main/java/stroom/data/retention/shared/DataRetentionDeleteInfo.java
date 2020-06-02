package stroom.data.retention.shared;

import java.util.Objects;

public class DataRetentionDeleteInfo {

    final String feedName;
    final int ruleNumber;
    final int daysTillDelete;

    public DataRetentionDeleteInfo(final String feedName,
                                   final int ruleNumber,
                                   final int daysTillDelete) {
        this.feedName = Objects.requireNonNull(feedName);
        this.ruleNumber = ruleNumber;
        this.daysTillDelete = daysTillDelete;
    }

    public String getFeedName() {
        return feedName;
    }

    public int getRuleNumber() {
        return ruleNumber;
    }

    public int getDaysTillDelete() {
        return daysTillDelete;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final DataRetentionDeleteInfo that = (DataRetentionDeleteInfo) o;
        return ruleNumber == that.ruleNumber &&
                daysTillDelete == that.daysTillDelete &&
                feedName.equals(that.feedName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(feedName, ruleNumber, daysTillDelete);
    }

    @Override
    public String toString() {
        return "DataRetentionDeleteInfo{" +
                "feedName='" + feedName + '\'' +
                ", ruleNumber=" + ruleNumber +
                ", daysTillDelete=" + daysTillDelete +
                '}';
    }
}
