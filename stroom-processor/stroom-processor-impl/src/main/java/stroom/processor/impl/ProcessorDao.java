package stroom.processor.impl;

import stroom.entity.shared.ExpressionCriteria;
import stroom.processor.shared.Processor;
import stroom.util.shared.ResultPage;
import stroom.util.shared.HasIntCrud;

import java.util.Optional;

public interface ProcessorDao extends HasIntCrud<Processor> {
    ResultPage<Processor> find(ExpressionCriteria criteria);

    Optional<Processor> fetchByUuid(final String uuid);
}
