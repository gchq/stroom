package stroom.processor.impl;

import stroom.entity.shared.ExpressionCriteria;
import stroom.processor.shared.ProcessorFilter;
import stroom.util.shared.HasIntCrud;
import stroom.util.shared.ResultPage;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface ProcessorFilterDao extends HasIntCrud<ProcessorFilter> {

    Optional<ProcessorFilter> fetchByUuid(String uuid);

    ResultPage<ProcessorFilter> find(ExpressionCriteria criteria);

    int logicalDeleteByProcessorFilterId(int processorFilterId);

    /**
     * Logically delete COMPLETE processor filters with no outstanding tasks where the tracker last poll is older
     * than the threshold. Note that COMPLETE just means that we have finished producing tasks on the DB, but we
     * can't delete the filter until all associated tasks have been processed otherwise they will never be picked
     * up.
     *
     * @param deleteThreshold Only logically delete filters with a last poll time older than the threshold.
     * @return The number of logically deleted filters.
     */
    int logicallyDeleteOldProcessorFilters(Instant deleteThreshold);

    ProcessorFilter restoreProcessorFilter(final ProcessorFilter processorFilter, final boolean resetTracker);

    /**
     * Physically delete old processor filters that are logically deleted with an update time older than the threshold.
     *
     * @param deleteThreshold Only physically delete filters with an update time older than the threshold.
     * @return The number of physically deleted filters.
     */
    Set<String> physicalDeleteOldProcessorFilters(Instant deleteThreshold);

    List<ProcessorFilter> fetchByRunAsUser(final String userUuid);
}
