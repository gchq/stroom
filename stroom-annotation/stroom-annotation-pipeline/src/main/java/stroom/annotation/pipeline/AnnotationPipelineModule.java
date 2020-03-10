package stroom.annotation.pipeline;
import stroom.pipeline.factory.PipelineElementModule;

public class AnnotationPipelineModule extends PipelineElementModule {
    @Override
    protected void configureElements() {
        bindElement(AnnotationWriter.class);
    }
}
