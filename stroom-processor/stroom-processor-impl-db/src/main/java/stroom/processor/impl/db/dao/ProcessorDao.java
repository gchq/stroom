package stroom.processor.impl.db.dao;

import stroom.entity.BasicCrudDao;
import stroom.entity.shared.BaseResultList;
import stroom.processor.shared.FindStreamProcessorCriteria;
import stroom.processor.shared.Processor;

public interface ProcessorDao extends BasicCrudDao<Processor> {

    BaseResultList<Processor> find(FindStreamProcessorCriteria criteria);
}
