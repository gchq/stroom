package stroom.meta.impl;

import stroom.data.retention.api.DataRetentionTracker;
import stroom.util.shared.Clearable;

import java.util.Optional;

public interface MetaRetentionTrackerDao extends Clearable {

    Optional<DataRetentionTracker> getTracker();

    void createOrUpdate(DataRetentionTracker dataRetentionTracker);
}
