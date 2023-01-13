package stroom.processor.impl;

import stroom.entity.shared.ExpressionCriteria;
import stroom.processor.shared.ProcessorFilter;
import stroom.util.shared.HasIntCrud;
import stroom.util.shared.ResultPage;

import java.time.Instant;

public interface ProcessorFilterDao extends HasIntCrud<ProcessorFilter> {

    ResultPage<ProcessorFilter> find(ExpressionCriteria criteria);

    int logicalDeleteByProcessorFilterId(int processorFilterId);

    int logicallyDeleteOldProcessorFilters(Instant deleteThreshold);

    void physicalDeleteOldProcessorFilters(Instant deleteThreshold);
}
