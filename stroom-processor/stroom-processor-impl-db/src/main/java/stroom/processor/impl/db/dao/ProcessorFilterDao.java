package stroom.processor.impl.db.dao;

import stroom.docref.DocRef;
import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.HasIntCrud;
import stroom.processor.shared.FindStreamProcessorFilterCriteria;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.QueryData;

public interface ProcessorFilterDao extends HasIntCrud<ProcessorFilter> {

    BaseResultList<ProcessorFilter> find(FindStreamProcessorFilterCriteria criteria);

    ProcessorFilter createNewFilter(final DocRef pipelineRef,
                                           final QueryData queryData,
                                           final boolean enabled,
                                           final int priority);
}
