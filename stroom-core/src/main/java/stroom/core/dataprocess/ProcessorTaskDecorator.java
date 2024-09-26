package stroom.core.dataprocess;

import stroom.docref.DocRef;
import stroom.processor.shared.ProcessorFilter;

public interface ProcessorTaskDecorator {

    DocRef getPipeline();

    void beforeProcessing(ProcessorFilter processorFilter);

    void afterProcessing();
}
