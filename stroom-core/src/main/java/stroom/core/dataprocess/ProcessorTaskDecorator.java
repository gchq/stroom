package stroom.core.dataprocess;

import stroom.docref.DocRef;
import stroom.meta.shared.Meta;
import stroom.processor.shared.ProcessorFilter;

public interface ProcessorTaskDecorator {

    void init(ProcessorFilter processorFilter);

    String getErrorFeedName(Meta meta);

    DocRef getPipeline();

    void beforeProcessing();

    void afterProcessing();
}
