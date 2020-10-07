package stroom.meta.impl;

import stroom.data.retention.api.DataRetentionTracker;

import java.util.Optional;

public interface MetaRetentionTrackerDao {
    Optional<DataRetentionTracker> getTracker();

    void createOrUpdate(DataRetentionTracker dataRetentionTracker);
}
