package stroom.data.retention.api;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

public class DataRetentionTracker {

    private final String rulesVersion;
    private final String ruleAge;
    private final Instant lastRunTime;

    public DataRetentionTracker(final String rulesVersion,
                                final String ruleAge,
                                final Instant lastRunTime) {
        this.lastRunTime = Objects.requireNonNull(lastRunTime.truncatedTo(ChronoUnit.MILLIS));
        this.rulesVersion = Objects.requireNonNull(rulesVersion);
        this.ruleAge = ruleAge;
    }

    public DataRetentionTracker(final String rulesVersion,
                                final String ruleAge,
                                final long lastRunTimeMs) {
        this(
                rulesVersion,
                ruleAge,
                Instant.ofEpochMilli(lastRunTimeMs));
    }

    public DataRetentionTracker copy(final Instant newLastRunTime) {
        return new DataRetentionTracker(rulesVersion, ruleAge, newLastRunTime);
    }

    public String getRulesVersion() {
        return rulesVersion;
    }

    public Instant getLastRunTime() {
        return lastRunTime;
    }

    public String getRuleAge() {
        return ruleAge;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DataRetentionTracker that = (DataRetentionTracker) o;
        return Objects.equals(rulesVersion, that.rulesVersion) && Objects.equals(ruleAge,
                that.ruleAge) && Objects.equals(lastRunTime, that.lastRunTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(rulesVersion, ruleAge, lastRunTime);
    }

    @Override
    public String toString() {
        return "DataRetentionTracker{" +
                "rulesVersion='" + rulesVersion + '\'' +
                ", ruleAge='" + ruleAge + '\'' +
                ", lastRunTime=" + lastRunTime +
                '}';
    }
}
