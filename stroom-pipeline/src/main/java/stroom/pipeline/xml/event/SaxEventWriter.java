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

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * A {@link ContentHandler} that encodes the SAX event stream it receives into a compact binary form,
 * decodable by {@link SaxEventReader}.
 * <p>
 * Encoding through the ContentHandler contract - rather than reaching into the {@link Event} classes -
 * means the same writer can serialise either a live SAX stream or a replayed {@link EventList} (via
 * {@link EventList#fire}), and that decoding fires straight back into a downstream handler with no XML
 * parse. The pipeline is ContentHandler-only (no lexical events), so a round trip reproduces the stream
 * without the namespace-declaration and locator drift that a serialise-then-re-parse through XML text
 * introduces.
 * <p>
 * Not thread safe; one instance encodes one stream. {@link #toByteArray()} returns the accumulated bytes.
 */
public final class SaxEventWriter implements ContentHandler {

    // Opcodes, one per ContentHandler callback. Shared with SaxEventReader (same package).
    static final byte SET_DOCUMENT_LOCATOR = 1;
    static final byte START_DOCUMENT = 2;
    static final byte END_DOCUMENT = 3;
    static final byte START_PREFIX_MAPPING = 4;
    static final byte END_PREFIX_MAPPING = 5;
    static final byte START_ELEMENT = 6;
    static final byte END_ELEMENT = 7;
    static final byte CHARACTERS = 8;
    static final byte IGNORABLE_WHITESPACE = 9;
    static final byte PROCESSING_INSTRUCTION = 10;
    static final byte SKIPPED_ENTITY = 11;

    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private final DataOutputStream out = new DataOutputStream(bytes);

    public byte[] toByteArray() {
        return bytes.toByteArray();
    }

    @Override
    public void setDocumentLocator(final Locator locator) {
        // The one ContentHandler method that does not throw SAXException. Snapshot the locator's values -
        // it is a live parser object, so only a snapshot can be stored; a replayed stream cannot reproduce
        // per-event positions (a known and accepted fidelity limit).
        try {
            out.writeByte(SET_DOCUMENT_LOCATOR);
            out.writeBoolean(locator != null);
            if (locator != null) {
                writeString(locator.getPublicId());
                writeString(locator.getSystemId());
                out.writeInt(locator.getLineNumber());
                out.writeInt(locator.getColumnNumber());
            }
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void startDocument() throws SAXException {
        writeOp(START_DOCUMENT);
    }

    @Override
    public void endDocument() throws SAXException {
        writeOp(END_DOCUMENT);
    }

    @Override
    public void startPrefixMapping(final String prefix, final String uri) throws SAXException {
        try {
            out.writeByte(START_PREFIX_MAPPING);
            writeString(prefix);
            writeString(uri);
        } catch (final IOException e) {
            throw new SAXException(e);
        }
    }

    @Override
    public void endPrefixMapping(final String prefix) throws SAXException {
        try {
            out.writeByte(END_PREFIX_MAPPING);
            writeString(prefix);
        } catch (final IOException e) {
            throw new SAXException(e);
        }
    }

    @Override
    public void startElement(final String uri,
                             final String localName,
                             final String qName,
                             final Attributes atts) throws SAXException {
        try {
            out.writeByte(START_ELEMENT);
            writeString(uri);
            writeString(localName);
            writeString(qName);
            final int count = atts == null ? 0 : atts.getLength();
            out.writeInt(count);
            for (int i = 0; i < count; i++) {
                writeString(atts.getURI(i));
                writeString(atts.getLocalName(i));
                writeString(atts.getQName(i));
                writeString(atts.getType(i));
                writeString(atts.getValue(i));
            }
        } catch (final IOException e) {
            throw new SAXException(e);
        }
    }

    @Override
    public void endElement(final String uri, final String localName, final String qName) throws SAXException {
        try {
            out.writeByte(END_ELEMENT);
            writeString(uri);
            writeString(localName);
            writeString(qName);
        } catch (final IOException e) {
            throw new SAXException(e);
        }
    }

    @Override
    public void characters(final char[] ch, final int start, final int length) throws SAXException {
        writeChars(CHARACTERS, ch, start, length);
    }

    @Override
    public void ignorableWhitespace(final char[] ch, final int start, final int length) throws SAXException {
        writeChars(IGNORABLE_WHITESPACE, ch, start, length);
    }

    @Override
    public void processingInstruction(final String target, final String data) throws SAXException {
        try {
            out.writeByte(PROCESSING_INSTRUCTION);
            writeString(target);
            writeString(data);
        } catch (final IOException e) {
            throw new SAXException(e);
        }
    }

    @Override
    public void skippedEntity(final String name) throws SAXException {
        try {
            out.writeByte(SKIPPED_ENTITY);
            writeString(name);
        } catch (final IOException e) {
            throw new SAXException(e);
        }
    }

    private void writeOp(final byte op) throws SAXException {
        try {
            out.writeByte(op);
        } catch (final IOException e) {
            throw new SAXException(e);
        }
    }

    private void writeChars(final byte op, final char[] ch, final int start, final int length) throws SAXException {
        try {
            out.writeByte(op);
            writeString(new String(ch, start, length));
        } catch (final IOException e) {
            throw new SAXException(e);
        }
    }

    // A nullable, length-prefixed UTF-8 string. writeUTF is deliberately not used - its 64KB cap is easily
    // exceeded by a single characters() event on a large record.
    private void writeString(final String s) throws IOException {
        if (s == null) {
            out.writeBoolean(false);
            return;
        }
        out.writeBoolean(true);
        final byte[] b = s.getBytes(StandardCharsets.UTF_8);
        out.writeInt(b.length);
        out.write(b);
    }
}
