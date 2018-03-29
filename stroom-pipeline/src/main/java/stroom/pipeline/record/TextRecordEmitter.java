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

package stroom.pipeline.record;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import stroom.pipeline.destination.Destination;
import stroom.pipeline.destination.DestinationProvider;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.filter.XMLFilterAdaptor;
import stroom.util.io.ByteSlice;
import stroom.util.io.StreamUtil;
import stroom.util.shared.Severity;

import javax.inject.Inject;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

public class TextRecordEmitter extends XMLFilterAdaptor {
    private final List<DestinationProvider> destinationProviders;
    private final StringBuilder builder = new StringBuilder();
    @Inject
    private ErrorReceiverProxy errorReceiverProxy;
    private int depth;

    public TextRecordEmitter(final List<DestinationProvider> destinationProviders) {
        this.destinationProviders = destinationProviders;
    }

    @Override
    public void startElement(final String uri, final String localName, final String qName, final Attributes atts)
            throws SAXException {
        depth++;
    }

    @Override
    public void endElement(final String uri, final String localName, final String qName) throws SAXException {
        if (depth == 2) {
            final String content = builder.toString();
            builder.setLength(0);

            final ByteSlice body = StreamUtil.getByteSlice(content);
            final Record record = new Record(null, null, body);
            for (final DestinationProvider destinationProvider : destinationProviders) {
                try {
                    final Destination destination = destinationProvider.borrowDestination();
                    final OutputStream outputStream = destination.getOutputStream(null, null);
                    if (outputStream != null) {
                    }
                } catch (final IOException e) {
                }
            }
        }

        depth--;
    }

    @Override
    public void characters(final char[] ch, final int start, final int length) throws SAXException {
        if (depth > 2) {
            builder.append(ch, start, length);
        }
    }

    protected void error(final String message, final RuntimeException e) {
        errorReceiverProxy.log(Severity.ERROR, null, getElementId(), message, e);
    }
}
