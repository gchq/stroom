package stroom.processor.api;

import stroom.processor.shared.ProcessorFilter;

public interface DataProcessorDecorator {

    void start(ProcessorFilter processorFilter);

    void end();
}
