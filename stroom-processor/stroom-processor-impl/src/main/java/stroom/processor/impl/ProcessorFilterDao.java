package stroom.processor.impl;

import stroom.entity.shared.ExpressionCriteria;
import stroom.processor.shared.ProcessorFilter;
import stroom.util.shared.HasIntCrud;
import stroom.util.shared.ResultPage;

public interface ProcessorFilterDao extends HasIntCrud<ProcessorFilter> {
    ResultPage<ProcessorFilter> find(final ExpressionCriteria criteria);
    ProcessorFilter create(final ProcessorFilter processorFilter, final Long trackerMinStreamId);
}
