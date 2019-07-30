package stroom.processor.impl;

import stroom.entity.shared.ExpressionCriteria;
import stroom.processor.shared.Processor;
import stroom.util.shared.BaseResultList;
import stroom.util.shared.HasIntCrud;

public interface ProcessorDao extends HasIntCrud<Processor> {
    BaseResultList<Processor> find(ExpressionCriteria criteria);
}
