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

package stroom.pipeline.refdata.store.offheapstore;

import net.sf.saxon.event.ReceivingContentHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

/**
 * {@link org.xml.sax.ContentHandler} for processing reference data values that are XML.
 * Because the value needs to be treated as an XML fragment we ignore the start/end document
 * events.
 */
public class FastInfosetContentHandler extends ReceivingContentHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(FastInfosetContentHandler.class);

    @Override
    public void startDocument() throws SAXException {
        LOGGER.trace("startDocument() - Ignored");
        // We are dealing in fragments so need to swallow these and not pass them on
    }

    @Override
    public void startPrefixMapping(final String prefix, final String uri) {
        LOGGER.trace("startPrefixMapping({}, {})", prefix, uri);
        super.startPrefixMapping(prefix, uri);
    }


    @Override
    public void endPrefixMapping(final String prefix) {
        LOGGER.trace("endPrefixMapping({}})", prefix);
        super.endPrefixMapping(prefix);
    }

    @Override
    public void startElement(final String uri, final String localname, final String rawname, final Attributes atts)
            throws SAXException {
        LOGGER.trace("startElement {} {} {}", uri, localname, rawname);
        super.startElement(uri, localname, rawname, atts);
    }

    @Override
    public void endElement(final String uri, final String localname, final String rawname) throws SAXException {
        LOGGER.trace("endElement {} {} {}", uri, localname, rawname);
        super.endElement(uri, localname, rawname);
    }

    @Override
    public void endDocument() throws SAXException {
        LOGGER.trace("endDocument() - Ignored");
        // We are dealing in fragments so need to swallow these and not pass them on
    }
}
