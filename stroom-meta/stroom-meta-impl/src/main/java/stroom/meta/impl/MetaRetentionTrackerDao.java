package stroom.meta.impl;

import stroom.data.retention.api.DataRetentionTracker;

import java.util.List;

public interface MetaRetentionTrackerDao {

    List<DataRetentionTracker> getTrackers();

    void createOrUpdate(final DataRetentionTracker dataRetentionTracker);

    int deleteTrackers(final String rulesVersion);
}
