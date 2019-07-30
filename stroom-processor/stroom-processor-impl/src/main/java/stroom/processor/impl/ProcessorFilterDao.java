package stroom.processor.impl;

import stroom.entity.shared.ExpressionCriteria;
import stroom.processor.shared.ProcessorFilter;
import stroom.util.shared.BaseResultList;
import stroom.util.shared.HasIntCrud;

public interface ProcessorFilterDao extends HasIntCrud<ProcessorFilter> {
    BaseResultList<ProcessorFilter> find(ExpressionCriteria criteria);
}
