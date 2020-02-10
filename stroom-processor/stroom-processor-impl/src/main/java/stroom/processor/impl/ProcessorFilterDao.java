package stroom.processor.impl;

import stroom.entity.shared.ExpressionCriteria;
import stroom.processor.shared.ProcessorFilter;
import stroom.util.shared.ResultList;
import stroom.util.shared.HasIntCrud;

public interface ProcessorFilterDao extends HasIntCrud<ProcessorFilter> {
    ResultList<ProcessorFilter> find(ExpressionCriteria criteria);
}
