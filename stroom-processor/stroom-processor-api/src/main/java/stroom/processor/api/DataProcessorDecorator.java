package stroom.processor.api;

import stroom.meta.shared.Meta;
import stroom.processor.shared.ProcessorFilter;

public interface DataProcessorDecorator {

    String getErrorFeedName(ProcessorFilter processorFilter, Meta meta);

    void start(ProcessorFilter processorFilter);

    void end();
}
