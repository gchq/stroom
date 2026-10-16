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

package stroom.pipeline.xml.event;

import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;
import org.xml.sax.helpers.LocatorImpl;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Decodes the binary form written by {@link SaxEventWriter} and fires it, in order, at a target
 * {@link ContentHandler}. This is the replay half: a stored element output can be pushed straight into a
 * downstream pipeline element with no XML parse.
 */
public final class SaxEventReader {

    private SaxEventReader() {
    }

    /**
     * Fire the encoded stream at {@code handler}, reproducing the original ContentHandler callbacks.
     */
    public static void replay(final byte[] data, final ContentHandler handler) throws SAXException {
        try (final DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            // ByteArrayInputStream#available returns the exact remaining count, so this reads the whole stream.
            while (in.available() > 0) {
                final byte op = in.readByte();
                switch (op) {
                    case SaxEventWriter.SET_DOCUMENT_LOCATOR -> {
                        if (in.readBoolean()) {
                            final LocatorImpl locator = new LocatorImpl();
                            locator.setPublicId(readString(in));
                            locator.setSystemId(readString(in));
                            locator.setLineNumber(in.readInt());
                            locator.setColumnNumber(in.readInt());
                            handler.setDocumentLocator(locator);
                        } else {
                            handler.setDocumentLocator(new LocatorImpl());
                        }
                    }
                    case SaxEventWriter.START_DOCUMENT -> handler.startDocument();
                    case SaxEventWriter.END_DOCUMENT -> handler.endDocument();
                    case SaxEventWriter.START_PREFIX_MAPPING -> {
                        final String prefix = readString(in);
                        final String uri = readString(in);
                        handler.startPrefixMapping(prefix, uri);
                    }
                    case SaxEventWriter.END_PREFIX_MAPPING -> handler.endPrefixMapping(readString(in));
                    case SaxEventWriter.START_ELEMENT -> {
                        final String uri = readString(in);
                        final String localName = readString(in);
                        final String qName = readString(in);
                        final int count = in.readInt();
                        final AttributesImpl atts = new AttributesImpl();
                        for (int i = 0; i < count; i++) {
                            atts.addAttribute(
                                    readString(in),   // uri
                                    readString(in),   // localName
                                    readString(in),   // qName
                                    readString(in),   // type
                                    readString(in));  // value
                        }
                        handler.startElement(uri, localName, qName, atts);
                    }
                    case SaxEventWriter.END_ELEMENT -> {
                        final String uri = readString(in);
                        final String localName = readString(in);
                        final String qName = readString(in);
                        handler.endElement(uri, localName, qName);
                    }
                    case SaxEventWriter.CHARACTERS -> {
                        final char[] ch = readString(in).toCharArray();
                        handler.characters(ch, 0, ch.length);
                    }
                    case SaxEventWriter.IGNORABLE_WHITESPACE -> {
                        final char[] ch = readString(in).toCharArray();
                        handler.ignorableWhitespace(ch, 0, ch.length);
                    }
                    case SaxEventWriter.PROCESSING_INSTRUCTION -> {
                        final String target = readString(in);
                        final String pi = readString(in);
                        handler.processingInstruction(target, pi);
                    }
                    case SaxEventWriter.SKIPPED_ENTITY -> handler.skippedEntity(readString(in));
                    default -> throw new SAXException("Unknown stepping SAX event opcode: " + op);
                }
            }
        } catch (final IOException e) {
            throw new SAXException(e);
        }
    }

    private static String readString(final DataInputStream in) throws IOException {
        if (!in.readBoolean()) {
            return null;
        }
        final int len = in.readInt();
        final byte[] b = new byte[len];
        in.readFully(b);
        return new String(b, StandardCharsets.UTF_8);
    }
}
