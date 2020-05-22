package stroom.processor.impl;

import stroom.entity.shared.ExpressionCriteria;
import stroom.processor.shared.ProcessorFilter;
import stroom.util.shared.HasIntCrud;
import stroom.util.shared.ResultPage;

public interface ProcessorFilterDao extends HasIntCrud<ProcessorFilter> {
    ResultPage<ProcessorFilter> find(ExpressionCriteria criteria);

    ProcessorFilter create(ProcessorFilter processorFilter,
                           Long minMetaCreateMs,
                           Long maxMetaCreateMs);

    boolean logicalDelete(int id);
}
