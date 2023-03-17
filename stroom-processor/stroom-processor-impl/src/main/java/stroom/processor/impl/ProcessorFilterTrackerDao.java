package stroom.processor.impl;

import stroom.processor.shared.ProcessorFilterTracker;

import java.util.Optional;

public interface ProcessorFilterTrackerDao {

    Optional<ProcessorFilterTracker> fetch(int id);

    int update(ProcessorFilterTracker processorFilterTracker);
}
