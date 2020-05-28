package stroom.data.retention.api;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

public class DataRetentionTracker {
    private final Instant lastRunTime;
    private final String rulesVersion;

    public DataRetentionTracker(final Instant lastRunTime, final String rulesVersion) {
        this.lastRunTime = Objects.requireNonNull(lastRunTime.truncatedTo(ChronoUnit.MILLIS));
        this.rulesVersion = Objects.requireNonNull(rulesVersion);
    }

    public DataRetentionTracker(final long lastRunTimeMs, final String rulesVersion) {
        this(Instant.ofEpochMilli(lastRunTimeMs), rulesVersion);
    }

    public Instant getLastRunTime() {
        return lastRunTime;
    }

    public String getRulesVersion() {
        return rulesVersion;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final DataRetentionTracker that = (DataRetentionTracker) o;
        return lastRunTime.equals(that.lastRunTime) &&
                rulesVersion.equals(that.rulesVersion);
    }

    @Override
    public int hashCode() {
        return Objects.hash(lastRunTime, rulesVersion);
    }

    @Override
    public String toString() {
        return "DataRetentionTracker{" +
                "lastRunTime=" + lastRunTime +
                ", rulesVersion='" + rulesVersion + '\'' +
                '}';
    }
}
