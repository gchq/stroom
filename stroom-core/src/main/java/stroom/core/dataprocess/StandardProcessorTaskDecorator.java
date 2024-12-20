package stroom.core.dataprocess;

import stroom.docref.DocRef;
import stroom.pipeline.shared.PipelineDoc;
import stroom.processor.shared.ProcessorFilter;

public class StandardProcessorTaskDecorator implements ProcessorTaskDecorator {

    private ProcessorFilter processorFilter;

    @Override
    public void beforeProcessing(final ProcessorFilter processorFilter) {
        this.processorFilter = processorFilter;
    }

    @Override
    public void afterProcessing() {

    }

    @Override
    public DocRef getPipeline() {
        return new DocRef(PipelineDoc.TYPE, processorFilter.getPipelineUuid());
    }
}
