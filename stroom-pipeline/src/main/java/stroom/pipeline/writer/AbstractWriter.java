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

package stroom.pipeline.writer;

import stroom.pipeline.destination.Destination;
import stroom.pipeline.destination.DestinationProvider;
import stroom.pipeline.errorhandler.ErrorReceiver;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.errorhandler.FatalErrorReceiver;
import stroom.pipeline.errorhandler.ProcessException;
import stroom.pipeline.factory.HasTargets;
import stroom.pipeline.factory.PipelineFactoryException;
import stroom.pipeline.factory.Target;
import stroom.pipeline.filter.AbstractXMLFilter;
import stroom.util.io.EncodingWriter;
import stroom.util.io.MultiOutputStream;
import stroom.util.io.NullOutputStream;
import stroom.util.io.OutputStreamWrapper;
import stroom.util.io.StreamUtil;
import stroom.util.shared.ErrorType;
import stroom.util.shared.Severity;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public abstract class AbstractWriter extends AbstractXMLFilter implements Target, HasTargets {
    private static final NullOutputStream NULL_OUTPUT_STREAM = new NullOutputStream();

    private final ErrorReceiver errorReceiver;
    private final List<DestinationProvider> destinationProviders = new ArrayList<>();
    private final Map<Destination, DestinationProvider> borrowedDestinations = new HashMap<>();
    private final OutputStreamWrapper outputStream = new OutputStreamWrapper();

    private EncodingWriter writer;
    private String encoding;

    AbstractWriter() {
        this.errorReceiver = new FatalErrorReceiver();
        outputStream.setOutputStream(NULL_OUTPUT_STREAM);
    }

    AbstractWriter(final ErrorReceiverProxy errorReceiverProxy) {
        this.errorReceiver = errorReceiverProxy;
        outputStream.setOutputStream(NULL_OUTPUT_STREAM);
    }

    protected OutputStreamWrapper getOutputStream() {
        return outputStream;
    }

    protected Writer getWriter() {
        if (writer == null) {
            if (getDestinationProviders().size() == 0) {
                throw ProcessException.create("No destination providers have been set");
            }

            Charset charset = StreamUtil.DEFAULT_CHARSET;
            if (encoding != null && !encoding.isEmpty()) {
                try {
                    charset = Charset.forName(encoding);
                } catch (final RuntimeException e) {
                    errorReceiver.log(
                            Severity.ERROR,
                            null,
                            getElementId(),
                            "Unsupported encoding '" + encoding + "', defaulting to 'UTF-8.'",
                            ErrorType.GENERIC,
                            e);
                }
            }

            writer = new EncodingWriter(outputStream, charset);
        }

        return writer;
    }

    protected Charset getCharset() {
        Charset charset = StreamUtil.DEFAULT_CHARSET;
        if (encoding != null && !encoding.isEmpty()) {
            try {
                charset = Charset.forName(encoding);
            } catch (final RuntimeException e) {
                error(e.getMessage(), e);
            }
        }
        return charset;
    }

    void borrowDestinations(final byte[] header, final byte[] footer) {
        if (borrowedDestinations.size() == 0) {
            final List<OutputStream> outputStreams = new ArrayList<>(destinationProviders.size());
            for (final DestinationProvider destinationProvider : destinationProviders) {
                try {
                    final Destination destination = destinationProvider.borrowDestination();
                    if (destination != null) {
                        borrowedDestinations.put(destination, destinationProvider);
                        final OutputStream outputStream = destination.getOutputStream(header, footer);
                        if (outputStream != null) {
                            outputStreams.add(outputStream);
                        }
                    }
                } catch (final IOException | RuntimeException e) {
                    fatal(e);
                }
            }

            if (outputStreams.size() == 0) {
                outputStream.setOutputStream(NULL_OUTPUT_STREAM);
            } else if (outputStreams.size() == 1) {
                outputStream.setOutputStream(outputStreams.get(0));
            } else {
                final OutputStream[] arr = outputStreams.toArray(new OutputStream[0]);
                outputStream.setOutputStream(new MultiOutputStream(arr));
            }
        }
    }

    void returnDestinations() {
        // Ensure the encoding writer is flushed before we return the
        // destinations to the providers.
        if (writer != null) {
            try {
                writer.flush();
            } catch (final IOException | RuntimeException e) {
                fatal(e);
            }
        }

        for (final Entry<Destination, DestinationProvider> entry : borrowedDestinations.entrySet()) {
            try {
                entry.getValue().returnDestination(entry.getKey());
            } catch (final IOException | RuntimeException e) {
                error(e);
            }
        }
        borrowedDestinations.clear();
        outputStream.setOutputStream(NULL_OUTPUT_STREAM);
    }

    @Override
    public void addTarget(final Target target) {
        if (!(target instanceof DestinationProvider)) {
            throw new PipelineFactoryException("Attempt to link to an element that is not a destination: "
                    + getElementId() + " > " + target.getElementId());
        }

        final DestinationProvider destinationProvider = (DestinationProvider) target;
        destinationProviders.add(destinationProvider);
    }

    @Override
    public void setTarget(final Target target) {
        destinationProviders.clear();
        if (target != null) {
            addTarget(target);
        }
    }

    @Override
    public void startProcessing() {
        for (final DestinationProvider destinationProvider : destinationProviders) {
            destinationProvider.startProcessing();
        }
        super.startProcessing();
    }

    /**
     * Called by the pipeline when processing of a file is complete.
     *
     * @see stroom.pipeline.filter.AbstractXMLFilter#endProcessing()
     */
    @Override
    public void endProcessing() {
        // Ensure all destinations are returned.
        returnDestinations();

        for (final DestinationProvider destinationProvider : destinationProviders) {
            try {
                destinationProvider.endProcessing();
            } catch (final RuntimeException e) {
                fatal(e);
            }
        }

        super.endProcessing();
    }

    @Override
    public void startStream() {
        for (final DestinationProvider destinationProvider : destinationProviders) {
            destinationProvider.startStream();
        }
        super.startStream();
    }

    @Override
    public void endStream() {
        for (final DestinationProvider destinationProvider : destinationProviders) {
            destinationProvider.endStream();
        }
        super.endStream();
    }

    protected void setEncoding(final String encoding) {
        this.encoding = encoding;
    }

    protected ErrorReceiver getErrorReceiver() {
        return errorReceiver;
    }

    private List<DestinationProvider> getDestinationProviders() {
        return destinationProviders;
    }

    protected void info(final String message, final Throwable t) {
        errorReceiver.log(Severity.INFO, null, getElementId(), message, t);
    }

    protected void warning(final String message, final Throwable t) {
        errorReceiver.log(Severity.WARNING, null, getElementId(), message, t);
    }

    protected void error(final String message, final Throwable t) {
        errorReceiver.log(Severity.ERROR, null, getElementId(), message, t);
    }

    protected void fatal(final String message, final Throwable t) {
        errorReceiver.log(Severity.FATAL_ERROR, null, getElementId(), message, t);
    }

    protected void info(final Throwable t) {
        info(t.getMessage(), t);
    }

    protected void warning(final Throwable t) {
        warning(t.getMessage(), t);
    }

    protected void error(final Throwable t) {
        error(t.getMessage(), t);
    }

    protected void fatal(final Throwable t) {
        fatal(t.getMessage(), t);
    }
}
