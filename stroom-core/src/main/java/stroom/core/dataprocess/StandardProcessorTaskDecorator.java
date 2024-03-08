package stroom.core.dataprocess;

import stroom.docref.DocRef;
import stroom.meta.shared.Meta;
import stroom.pipeline.shared.PipelineDoc;
import stroom.processor.shared.ProcessorFilter;

public class StandardProcessorTaskDecorator implements ProcessorTaskDecorator {

    private ProcessorFilter processorFilter;

    @Override
    public void init(final ProcessorFilter processorFilter) {
        this.processorFilter = processorFilter;
    }

    @Override
    public String getErrorFeedName(final Meta meta) {
        return meta.getFeedName();
    }

    @Override
    public DocRef getPipeline() {
        return new DocRef(PipelineDoc.DOCUMENT_TYPE, processorFilter.getPipelineUuid());
    }

    @Override
    public void beforeProcessing() {

    }

    @Override
    public void afterProcessing() {

    }
}
