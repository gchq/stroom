/*
 * Copyright 2016 Crown Copyright
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
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.util.shared.Severity;

import java.io.IOException;
import java.io.OutputStream;

public abstract class AbstractAppender extends AbstractDestinationProvider implements Destination {
    private final ErrorReceiverProxy errorReceiverProxy;

    private OutputStream outputStream;
    private byte[] footer;

    AbstractAppender(final ErrorReceiverProxy errorReceiverProxy) {
        this.errorReceiverProxy = errorReceiverProxy;
    }

    @Override
    public void endProcessing() {
        if (outputStream != null) {
            try {
                writeFooter();
            } catch (final IOException e) {
                error(e.getMessage(), e);
            }

            try {
                outputStream.close();
            } catch (final IOException e) {
                error(e.getMessage(), e);
            }
        }

        super.endProcessing();
    }

    @Override
    public Destination borrowDestination() throws IOException {
        return this;
    }

    @Override
    public void returnDestination(final Destination destination) throws IOException {
    }

    @Override
    public final OutputStream getByteArrayOutputStream() throws IOException {
        return getOutputStream(null, null);
    }

    @Override
    public OutputStream getOutputStream(final byte[] header, final byte[] footer) throws IOException {
        this.footer = footer;

        if (outputStream == null) {
            outputStream = createOutputStream();

            // If we haven't written yet then create the output stream and
            // write a header if we have one.
            if (header != null && header.length > 0) {
                // Write the header.
                write(header);
            }
        }

        return outputStream;
    }

    private void writeFooter() throws IOException {
        if (footer != null && footer.length > 0) {
            // Write the footer.
            write(footer);
        }
    }

    private void write(final byte[] bytes) throws IOException {
        outputStream.write(bytes, 0, bytes.length);
    }

    protected abstract OutputStream createOutputStream() throws IOException;

    protected void error(final String message, final Exception e) {
        errorReceiverProxy.log(Severity.ERROR, null, getElementId(), message, e);
    }
}
