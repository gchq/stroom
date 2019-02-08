package stroom.processor.impl.db.dao;

import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.HasIntCrud;
import stroom.processor.shared.FindStreamProcessorCriteria;
import stroom.processor.shared.Processor;

public interface ProcessorDao extends HasIntCrud<Processor> {

    BaseResultList<Processor> find(FindStreamProcessorCriteria criteria);
}
