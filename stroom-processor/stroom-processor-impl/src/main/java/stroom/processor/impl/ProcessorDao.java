package stroom.processor.impl;

import stroom.entity.shared.ExpressionCriteria;
import stroom.processor.shared.Processor;
import stroom.util.shared.ResultList;
import stroom.util.shared.HasIntCrud;

public interface ProcessorDao extends HasIntCrud<Processor> {
    ResultList<Processor> find(ExpressionCriteria criteria);
}
