package stroom.processor.impl;

import stroom.processor.shared.FindProcessorFilterCriteria;
import stroom.processor.shared.ProcessorFilter;
import stroom.util.shared.BaseResultList;
import stroom.util.shared.HasIntCrud;

public interface ProcessorFilterDao extends HasIntCrud<ProcessorFilter> {
    BaseResultList<ProcessorFilter> find(FindProcessorFilterCriteria criteria);
}
