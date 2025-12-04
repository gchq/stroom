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

package stroom.pipeline.reader;

import stroom.pipeline.destination.Destination;
import stroom.pipeline.destination.DestinationProvider;
import stroom.pipeline.errorhandler.ProcessException;
import stroom.pipeline.factory.AbstractElement;
import stroom.pipeline.factory.Element;
import stroom.pipeline.factory.HasTargets;
import stroom.pipeline.factory.Processor;
import stroom.pipeline.factory.TakesInput;
import stroom.pipeline.factory.TakesReader;
import stroom.pipeline.factory.Target;
import stroom.util.io.StreamUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PipedReader;
import java.io.PipedWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class AbstractIOElement extends AbstractElement implements HasTargets {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractIOElement.class);
    private final List<Element> targetList = new ArrayList<>();
    private InputStream inputStream;
    private Reader reader;
    private String encoding;

    @Override
    public void addTarget(final Target target) {
        if (target != null) {
            targetList.add(target);
        }
    }

    @Override
    public void setTarget(final Target target) {
        targetList.clear();
        addTarget(target);
    }

    protected void setInputStream(final InputStream inputStream, final String encoding) throws IOException {
        this.inputStream = inputStream;
        this.encoding = encoding;
    }

    protected void setReader(final Reader reader) throws IOException {
        this.reader = reader;
    }

    @Override
    public List<Processor> createProcessors() {
        try {
            if (targetList.isEmpty()) {
                return Collections.emptyList();
            }

            // See if we have a mixture of TakesInput elements and
            // DestinationProviders or more than one TakesInput elements. If we
            // do then we have to fork the process.
            int destinationProviderCount = 0;
            int takesInputCount = 0;
            int takesReaderCount = 0;

            for (final Element target : targetList) {
                if (target instanceof DestinationProvider) {
                    destinationProviderCount++;
                } else if (target instanceof TakesInput) {
                    takesInputCount++;
                } else if (target instanceof TakesReader) {
                    takesReaderCount++;
                }
            }

            final boolean forkProcess = takesInputCount + takesReaderCount > 1
                    || (takesInputCount + takesReaderCount == 1 && destinationProviderCount > 0);

            final List<Processor> processors = new ArrayList<>(takesInputCount + takesReaderCount + 1);
            final List<DestinationProvider> destinationProviders = new ArrayList<>(destinationProviderCount);
            final List<OutputStream> outputStreams = new ArrayList<>(takesInputCount);
            final List<Writer> writers = new ArrayList<>(takesReaderCount);

            for (final Element target : targetList) {
                if (target instanceof DestinationProvider) {
                    destinationProviders.add((DestinationProvider) target);

                } else if (target instanceof TakesInput && inputStream != null) {
                    final TakesInput takesInput = (TakesInput) target;
                    takesInput.setInputStream(inputStream, encoding);

                    // Create child processors.
                    final List<Processor> childProcessors = takesInput.createProcessors();

                    if (forkProcess) {
                        // Only add a piped output stream if we are going to
                        // have child processors consume the data.
                        if (childProcessors.size() > 0) {
                            final PipedInputStream pipedInputStream = new PipedInputStream();
                            final PipedOutputStream pipedOutputStream = new PipedOutputStream(pipedInputStream);
                            takesInput.setInputStream(pipedInputStream, encoding);
                            outputStreams.add(pipedOutputStream);
                            processors.addAll(childProcessors);
                        }

                    } else {
                        processors.addAll(childProcessors);
                    }

                } else if (target instanceof TakesReader && reader != null) {
                    final TakesReader takesReader = (TakesReader) target;
                    takesReader.setReader(reader);

                    // Create child processors.
                    final List<Processor> childProcessors = takesReader.createProcessors();

                    if (forkProcess) {
                        // Only add a piped writer if we are going to have
                        // child processors consume the data.
                        if (childProcessors.size() > 0) {
                            final PipedReader pipedReader = new PipedReader();
                            final PipedWriter pipedWriter = new PipedWriter(pipedReader);
                            takesReader.setReader(pipedReader);
                            writers.add(pipedWriter);
                            processors.addAll(childProcessors);
                        }
                    } else {
                        processors.addAll(childProcessors);
                    }
                }
            }

            if (inputStream != null) {
                if (destinationProviders.size() > 0 || outputStreams.size() > 0) {
                    final DestinationOutputProcessor destProc = new DestinationOutputProcessor(destinationProviders,
                            outputStreams);
                    destProc.setInputStream(inputStream);
                    processors.add(destProc);
                }
            } else if (reader != null) {
                if (destinationProviders.size() > 0 || writers.size() > 0) {
                    final DestinationWriterProcessor destProc = new DestinationWriterProcessor(destinationProviders,
                            writers);
                    destProc.setReader(reader);
                    processors.add(destProc);
                }
            }

            return processors;
        } catch (final RuntimeException | IOException e) {
            throw ProcessException.wrap(e);
        }
    }

    @Override
    public void startProcessing() {
        for (final Element target : targetList) {
            target.startProcessing();
        }
    }

    @Override
    public void endProcessing() {
        for (final Element target : targetList) {
            target.endProcessing();
        }
    }

    @Override
    public void startStream() {
        for (final Element target : targetList) {
            target.startStream();
        }
    }

    @Override
    public void endStream() {
        for (final Element target : targetList) {
            target.endStream();
        }
    }

    private abstract static class DestinationProcessor implements Processor {

        private final List<DestinationProvider> destinationProviders;
        private Map<DestinationProvider, Destination> destinationMap;

        DestinationProcessor(final List<DestinationProvider> destinationProviders) {
            this.destinationProviders = destinationProviders;
        }

        void borrowDestinations() throws IOException {
            IOException exception = null;

            if (destinationMap == null) {
                destinationMap = new HashMap<>();
                for (final DestinationProvider destinationProvider : destinationProviders) {
                    try {
                        final Destination destination = destinationProvider.borrowDestination();
                        destinationMap.put(destinationProvider, destination);
                    } catch (final IOException e) {
                        LOGGER.error(e.getMessage(), e);
                        exception = e;
                    }
                }
            }

            if (exception != null) {
                throw exception;
            }
        }

        void returnDestinations() throws IOException {
            IOException exception = null;

            if (destinationMap != null) {
                for (final Entry<DestinationProvider, Destination> entry : destinationMap.entrySet()) {
                    try {
                        entry.getKey().returnDestination(entry.getValue());
                    } catch (final IOException e) {
                        LOGGER.error(e.getMessage(), e);
                        exception = e;
                    }
                }
                destinationMap = null;
            }

            if (exception != null) {
                throw exception;
            }
        }

        protected Collection<Destination> getDestinations() {
            return destinationMap.values();
        }
    }

    private static class DestinationOutputProcessor extends DestinationProcessor {

        private final List<OutputStream> otherOutputStreams;
        private InputStream inputStream;

        DestinationOutputProcessor(final List<DestinationProvider> destinationProviders,
                                   final List<OutputStream> otherOutputStreams) {
            super(destinationProviders);
            this.otherOutputStreams = otherOutputStreams;
        }

        @Override
        public void process() {
            try {
                try {
                    borrowDestinations();

                    final List<OutputStream> outputStreams = new ArrayList<>(otherOutputStreams);
                    for (final Destination destination : getDestinations()) {
                        outputStreams.add(destination.getOutputStream());
                    }

                    if (inputStream != null) {
                        final byte[] buffer = new byte[8192];
                        int len;
                        while ((len = inputStream.read(buffer)) != -1) {
                            for (final OutputStream outputStream : outputStreams) {
                                outputStream.write(buffer, 0, len);
                            }
                        }
                    }
                    for (final OutputStream outputStream : outputStreams) {
                        outputStream.flush();

                        // If the output stream is a piped output stream then we
                        // must close it or it will block other upstream
                        // processors.
                        if (outputStream instanceof PipedOutputStream) {
                            outputStream.close();
                        }
                    }

                } finally {
                    returnDestinations();
                }
            } catch (final RuntimeException | IOException e) {
                throw ProcessException.wrap(e);
            }
        }

        public void setInputStream(final InputStream inputStream) {
            this.inputStream = inputStream;
        }
    }


    // --------------------------------------------------------------------------------


    private static class DestinationWriterProcessor extends DestinationProcessor {

        private final List<Writer> otherWriters;
        private Reader reader;

        DestinationWriterProcessor(final List<DestinationProvider> destinationProviders,
                                   final List<Writer> otherWriters) {
            super(destinationProviders);
            this.otherWriters = otherWriters;
        }

        @Override
        public void process() {
            try {
                try {
                    borrowDestinations();

                    final List<Writer> writers = new ArrayList<>(otherWriters);
                    for (final Destination destination : getDestinations()) {
                        writers.add(new OutputStreamWriter(destination.getOutputStream(),
                                StreamUtil.DEFAULT_CHARSET));
                    }

                    final char[] buffer = new char[8192];
                    int len;
                    while ((len = reader.read(buffer)) != -1) {
                        for (final Writer writer : writers) {
                            writer.write(buffer, 0, len);
                        }
                    }
                    for (final Writer writer : writers) {
                        writer.flush();

                        // If the writer is a piped writer then we must close it
                        // or it will block other upstream processors.
                        if (writer instanceof PipedWriter) {
                            writer.close();
                        }
                    }

                } finally {
                    returnDestinations();
                }
            } catch (final RuntimeException | IOException e) {
                throw ProcessException.wrap(e);
            }
        }

        public void setReader(final Reader reader) {
            this.reader = reader;
        }
    }
}
