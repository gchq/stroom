package stroom.processor.impl;

import stroom.entity.shared.ExpressionCriteria;
import stroom.processor.shared.Processor;
import stroom.util.shared.HasIntCrud;
import stroom.util.shared.ResultPage;

import java.time.Instant;
import java.util.Optional;

public interface ProcessorDao extends HasIntCrud<Processor> {

    ResultPage<Processor> find(ExpressionCriteria criteria);

    Optional<Processor> fetchByUuid(String uuid);

    Optional<Processor> fetchByPipelineUuid(String pipelineUuid);

    /**
     * Will also logically delete all associated processor filters.
     *
     * @return True if the processor is deleted.
     */
    int logicalDeleteByProcessorId(int processorId);

    int physicalDeleteOldProcessors(Instant deleteThreshold);
}
