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

package stroom.pipeline.server.xsltfunctions;

import net.sf.saxon.Configuration;
import net.sf.saxon.event.Builder;
import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.event.ReceivingContentHandler;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.EmptyAtomicSequence;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.tree.tiny.TinyBuilder;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;
import stroom.pipeline.state.LocationHolder;
import stroom.pipeline.state.LocationHolder.FunctionType;
import stroom.pipeline.state.StreamHolder;
import stroom.util.shared.Severity;
import stroom.pipeline.shared.SourceLocation;
import stroom.util.spring.StroomScope;

import javax.inject.Inject;

@Component
@Scope(StroomScope.PROTOTYPE)
class Location extends StroomExtensionFunctionCall {
    private static final AttributesImpl EMPTY_ATTS = new AttributesImpl();
    private static final String URI = "stroom";

    private final StreamHolder streamHolder;
    private final LocationHolder locationHolder;

    @Inject
    Location(final StreamHolder streamHolder,
             final LocationHolder locationHolder) {
        this.streamHolder = streamHolder;
        this.locationHolder = locationHolder;
    }

    @Override
    protected Sequence call(final String functionName, final XPathContext context, final Sequence[] arguments) {
        Sequence result = EmptyAtomicSequence.getInstance();

        try {
            locationHolder.move(FunctionType.LOCATION);
            final long streamId = streamHolder.getStream().getId();
            final SourceLocation currentLocation = locationHolder.getCurrentLocation();
            if (currentLocation != null) {
                result = createSequence(context, streamId, currentLocation);
            }
        } catch (final Exception e) {
            log(context, Severity.ERROR, e.getMessage(), e);
        }

        return result;
    }

    private Sequence createSequence(final XPathContext context, final long streamId, final SourceLocation location) throws SAXException {
        final Configuration configuration = context.getConfiguration();
        final PipelineConfiguration pipe = configuration.makePipelineConfiguration();
        final Builder builder = new TinyBuilder(pipe);

        final ReceivingContentHandler contentHandler = new ReceivingContentHandler();
        contentHandler.setPipelineConfiguration(pipe);
        contentHandler.setReceiver(builder);

        contentHandler.startDocument();
        startElement(contentHandler, "location");
        data(contentHandler, "streamId", location.getStreamId());
        data(contentHandler, "streamNo", location.getStreamNo());
        data(contentHandler, "recordNo", location.getRecordNo());
        data(contentHandler, "lineFrom", location.getHighlight().getFrom().getLineNo());
        data(contentHandler, "colFrom", location.getHighlight().getFrom().getColNo());
        data(contentHandler, "lineTo", location.getHighlight().getTo().getLineNo());
        data(contentHandler, "colTo", location.getHighlight().getTo().getColNo());
        endElement(contentHandler, "location");
        contentHandler.endDocument();

        Sequence sequence = builder.getCurrentRoot();

        // Reset the builder, detaching it from the constructed
        // document.
        builder.reset();

        return sequence;
    }

    private void data(final ReceivingContentHandler contentHandler, final String name, final long value) throws SAXException {
        startElement(contentHandler, name);
        characters(contentHandler, String.valueOf(value));
        endElement(contentHandler, name);
    }

    private void startElement(final ReceivingContentHandler contentHandler, final String name) throws SAXException {
        contentHandler.startElement(URI, name, name, EMPTY_ATTS);
    }

    private void endElement(final ReceivingContentHandler contentHandler, final String name) throws SAXException {
        contentHandler.endElement(URI, name, name);
    }

    private void characters(final ReceivingContentHandler contentHandler, final String characters) throws SAXException {
        final char[] chars = characters.toCharArray();
        contentHandler.characters(chars, 0, chars.length);
    }
}
