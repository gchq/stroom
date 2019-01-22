package stroom.processor.impl.db.dao;

import stroom.entity.BasicCrudDao;
import stroom.entity.shared.BaseResultList;
import stroom.processor.shared.FindStreamProcessorFilterCriteria;
import stroom.processor.shared.ProcessorFilter;

public interface ProcessorFilterDao extends BasicCrudDao<ProcessorFilter> {

    BaseResultList<ProcessorFilter> find(FindStreamProcessorFilterCriteria criteria);
}
