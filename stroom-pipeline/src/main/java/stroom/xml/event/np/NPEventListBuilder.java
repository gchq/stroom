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

package stroom.xml.event.np;

import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

import stroom.xml.event.EventList;
import stroom.xml.event.EventListBuilder;

public final class NPEventListBuilder implements EventListBuilder {
    private static final String EMPTY = "";

    private static final NPEventListNamePool namePool = new NPEventListNamePool();

    public byte[] eventTypeArr;
    public int[] nameCodeArr;
    public NPAttributes[] attsArr;
    public char[] charArr;
    public int[] charPosArr;

    public int eventTypeIndex = 0;
    public int nameCodeIndex = 0;
    public int attsIndex = 0;
    public int charIndex = 0;
    public int charPosIndex = 0;
    private int bufferLength = 0;

    private int pos = 0;

    public NPEventListBuilder() {
        reset();
    }

    @Override
    public EventList getEventList() {
        byte[] eventTypeArrTmp = null;
        int[] nameCodeArrTmp = null;
        NPAttributes[] attsArrTmp = null;
        char[] charArrTmp = null;
        int[] charPosArrTmp = null;

        // Do a final flush.
        flush();

        if (eventTypeArr != null) {
            eventTypeArrTmp = new byte[eventTypeIndex];
            System.arraycopy(eventTypeArr, 0, eventTypeArrTmp, 0, eventTypeIndex);
        }

        if (nameCodeArr != null) {
            nameCodeArrTmp = new int[nameCodeIndex];
            System.arraycopy(nameCodeArr, 0, nameCodeArrTmp, 0, nameCodeIndex);
        }

        if (attsArr != null) {
            attsArrTmp = new NPAttributes[attsIndex];
            System.arraycopy(attsArr, 0, attsArrTmp, 0, attsIndex);
        }

        if (charArr != null) {
            charArrTmp = new char[charIndex];
            System.arraycopy(charArr, 0, charArrTmp, 0, charIndex);
        }

        if (charPosArr != null) {
            charPosArrTmp = new int[charPosIndex];
            System.arraycopy(charPosArr, 0, charPosArrTmp, 0, charPosIndex);
        }

        return new NPEventList(namePool, eventTypeArrTmp, nameCodeArrTmp, attsArrTmp, charArrTmp, charPosArrTmp);
    }

    @Override
    public void reset() {
        // Reset vars for next use.
        eventTypeIndex = 0;
        nameCodeIndex = 0;
        attsIndex = 0;
        charIndex = 0;
        charPosIndex = 0;
        bufferLength = 0;
        pos = 0;
    }

    @Override
    public void setDocumentLocator(final Locator locator) {
    }

    @Override
    public void startDocument() throws SAXException {
    }

    @Override
    public void endDocument() throws SAXException {
    }

    @Override
    public void startPrefixMapping(final String prefix, final String uri) throws SAXException {
    }

    @Override
    public void endPrefixMapping(final String prefix) throws SAXException {
    }

    @Override
    public void startElement(final String uri, final String localName, final String qName, Attributes atts)
            throws SAXException {
        flush();

        ensureEventCapacity();
        if (atts == null || atts.getLength() == 0) {
            eventTypeArr[eventTypeIndex++] = NPEventList.START_ELEMENT;
        } else {
            eventTypeArr[eventTypeIndex++] = NPEventList.START_ELEMENT_WITH_ATTS;

            ensureAttCapacity();
            attsArr[attsIndex++] = new NPAttributes(namePool, atts);
        }

        ensureNameCodeCapacity();
        nameCodeArr[nameCodeIndex++] = namePool.allocate(EMPTY, uri, localName);
    }

    @Override
    public void endElement(final String uri, final String localName, final String qName) throws SAXException {
        flush();
        ensureEventCapacity();
        eventTypeArr[eventTypeIndex++] = NPEventList.END_ELEMENT;
    }

    @Override
    public void characters(final char[] ch, final int start, final int length) throws SAXException {
        ensureCharCapacity(length);
        System.arraycopy(ch, start, charArr, pos, length);
        pos += length;
    }

    private void flush() {
        if (charIndex != pos) {
            charIndex = pos;

            ensureCharPosCapacity();
            charPosArr[charPosIndex++] = pos;
            ensureEventCapacity();
            eventTypeArr[eventTypeIndex++] = NPEventList.CHARACTERS;
        }
    }

    private void ensureEventCapacity() {
        if (eventTypeArr == null) {
            eventTypeArr = new byte[10];
        } else if (eventTypeArr.length == eventTypeIndex + 1) {
            final byte[] tmp = new byte[eventTypeArr.length * 2];
            System.arraycopy(eventTypeArr, 0, tmp, 0, eventTypeArr.length);
            eventTypeArr = tmp;
        }
    }

    private void ensureNameCodeCapacity() {
        if (nameCodeArr == null) {
            nameCodeArr = new int[10];
        } else if (nameCodeArr.length == nameCodeIndex + 1) {
            final int[] tmp = new int[nameCodeArr.length * 2];
            System.arraycopy(nameCodeArr, 0, tmp, 0, nameCodeArr.length);
            nameCodeArr = tmp;
        }
    }

    private void ensureCharPosCapacity() {
        if (charPosArr == null) {
            charPosArr = new int[10];
        } else if (charPosArr.length == charPosIndex + 1) {
            final int[] tmp = new int[charPosArr.length * 2];
            System.arraycopy(charPosArr, 0, tmp, 0, charPosArr.length);
            charPosArr = tmp;
        }
    }

    private void ensureAttCapacity() {
        if (attsArr == null) {
            attsArr = new NPAttributes[10];
        } else if (attsArr.length == attsIndex + 1) {
            final NPAttributes[] tmp = new NPAttributes[attsArr.length * 2];
            System.arraycopy(attsArr, 0, tmp, 0, attsArr.length);
            attsArr = tmp;
        }
    }

    private void ensureCharCapacity(final int length) {
        int newLen = bufferLength + length;

        // Ensure the buffer is big enough.
        if (charArr == null) {
            charArr = new char[newLen];
        } else if (charArr.length < newLen) {
            int len = charArr.length;
            while (len < newLen) {
                len = len * 2;
            }

            final char[] tmp = new char[len];
            System.arraycopy(charArr, 0, tmp, 0, bufferLength);
            charArr = tmp;
        }

        bufferLength = newLen;
    }

    @Override
    public void ignorableWhitespace(final char[] ch, final int start, final int length) throws SAXException {
    }

    @Override
    public void processingInstruction(final String target, final String data) throws SAXException {
    }

    @Override
    public void skippedEntity(String name) throws SAXException {
    }
}
