package stroom.processor.impl.db.dao;

import stroom.entity.shared.BaseResultList;
import stroom.processor.shared.FindStreamProcessorFilterCriteria;
import stroom.processor.shared.ProcessorFilter;

public interface ProcessorFilterDao {

    ProcessorFilter create();

    ProcessorFilter update(ProcessorFilter processor);

    int delete(int id);

    ProcessorFilter fetch(int id);

    BaseResultList<ProcessorFilter> find(FindStreamProcessorFilterCriteria criteria);
}
