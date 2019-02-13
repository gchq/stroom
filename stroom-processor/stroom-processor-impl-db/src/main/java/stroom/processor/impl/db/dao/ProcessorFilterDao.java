package stroom.processor.impl.db.dao;

import stroom.docref.DocRef;
import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.HasIntCrud;
import stroom.processor.shared.FindStreamProcessorFilterCriteria;
import stroom.processor.shared.Processor;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.QueryData;

public interface ProcessorFilterDao extends HasIntCrud<ProcessorFilter> {

    BaseResultList<ProcessorFilter> find(FindStreamProcessorFilterCriteria criteria);

    ProcessorFilter createFilter(final DocRef pipelineRef,
                                 final QueryData queryData,
                                 final boolean enabled,
                                 final int priority);

    ProcessorFilter createFilter(final Processor processor,
                                 final QueryData queryData,
                                 final boolean enabled,
                                 final int priority);
}
