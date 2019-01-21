package stroom.processor.impl.db.dao;

import stroom.entity.shared.BaseResultList;
import stroom.processor.shared.FindStreamProcessorCriteria;
import stroom.processor.shared.Processor;

public interface ProcessorDao {

    Processor create();

    Processor update(Processor processor);

    int delete(int id);

    Processor fetch(int id);

    BaseResultList<Processor> find(FindStreamProcessorCriteria criteria);
}
