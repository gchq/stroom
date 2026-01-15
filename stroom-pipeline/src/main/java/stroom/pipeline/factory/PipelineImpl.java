/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.pipeline.factory;

import stroom.pipeline.errorhandler.ProcessException;
import stroom.util.io.StreamUtil;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PipelineImpl implements Pipeline {

    private final ProcessorFactory processorFactory;
    private final Map<String, Element> elementInstances;
    private final TakesInput rootElement;
    private final boolean stepping;

    private boolean externallyStartedProcessing;
    private boolean externallyStartedStream;

    public PipelineImpl(final ProcessorFactory processorFactory, final Map<String, Element> elementInstances,
                        final TakesInput rootElement, final boolean stepping) {
        this.processorFactory = processorFactory;
        this.elementInstances = elementInstances;
        this.rootElement = rootElement;
        this.stepping = stepping;
    }

    @Override
    public void startProcessing() {
        externallyStartedProcessing = true;
        internalStartProcessing();
    }

    @Override
    public void endProcessing() {
        externallyStartedProcessing = false;
        internalEndProcessing();
    }

    @Override
    public void startStream() {
        externallyStartedStream = true;
        internalStartStream();
    }

    @Override
    public void endStream() {
        externallyStartedStream = false;
        internalEndStream();
    }

    private void internalStartProcessing() {
        rootElement.startProcessing();
    }

    private void internalEndProcessing() {
        rootElement.endProcessing();
    }

    private void internalStartStream() {
        rootElement.startStream();
    }

    private void internalEndStream() {
        rootElement.endStream();
    }

    @Override
    public void process(final InputStream inputStream) {
        process(inputStream, StreamUtil.DEFAULT_CHARSET_NAME);
    }

    @Override
    public void process(final InputStream inputStream, final String encoding) {
        try {
            rootElement.setInputStream(inputStream, encoding);
        } catch (final IOException e) {
            throw ProcessException.wrap(e);
        }

        try {
            if (!externallyStartedProcessing) {
                internalStartProcessing();
            }

            try {
                if (!externallyStartedStream) {
                    internalStartStream();
                }

                final List<Processor> processors = rootElement.createProcessors();
                final Processor processor = processorFactory.create(processors);
                if (processor == null) {
                    throw ProcessException.create("The pipeline contains no child elements capable of processing");
                }

                if (stepping && processor instanceof ProcessorFactoryImpl.MultiWayProcessor) {
                    // FIXME : At present we can't support stepping where we have a
                    // multi-way processor as multi-way processors have
                    // PipedInput/PipedOutput or PipedReader/PipedWriter instances that
                    // run in separate threads to both produce and consume data
                    // respectively. As separate threads are used stepping cannot
                    // reliably get a snapshot of the data IO at each pipeline element.
                    // To fix this some sort of synchronisation needs to occur between
                    // all threads when a step is detected and before IO is captured or
                    // cleaned. This will be difficult to fix so leaving for now.
                    throw ProcessException.create("Stepping mode is not currently supported on forked pipelines " +
                            "that require piped IO to process them");
                }

                processor.process();

            } finally {
                if (!externallyStartedStream) {
                    internalEndStream();
                }
            }
        } finally {
            if (!externallyStartedProcessing) {
                internalEndProcessing();
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends Element> List<T> findFilters(final Class<T> clazz) {
        final List<T> filters = new ArrayList<>();
        for (final Element pipelineElement : elementInstances.values()) {
            if (clazz.isAssignableFrom(pipelineElement.getClass())) {
                filters.add((T) pipelineElement);
            }
        }
        return filters;
    }
}
