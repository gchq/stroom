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

import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.event.ReceiverOptions;
import net.sf.saxon.expr.parser.Location;
import net.sf.saxon.om.CodedName;
import net.sf.saxon.om.NamePool;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.tiny.CharSlice;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.Untyped;

import java.util.HashMap;
import java.util.Map;

public class EventListConsumer {
    private static final Location NULL_LOCATION = new NullLocation();

    private static final String EMPTY = "";
    private final Receiver receiver;
    private final PipelineConfiguration pipe;
    private final NamePool pool;

    private final Map<Integer, Integer> codeMap = new HashMap<>();

    public EventListConsumer(final Receiver receiver, final PipelineConfiguration pipe) {
        this.receiver = receiver;
        this.pipe = pipe;

        pool = pipe.getConfiguration().getNamePool();
    }

    public void startDocument() throws XPathException {
        receiver.setPipelineConfiguration(pipe);
        receiver.open();
        receiver.startDocument(0);
    }

    public void endDocument() throws XPathException {
        receiver.endDocument();
        receiver.close();
    }

    public void consume(final NPEventList eventList) throws XPathException {
        final NPEventListNamePool namePool = eventList.namePool;

        int eventTypeIndex;
        int nameCodeIndex = 0;

        int attsIndex = 0;
        int charPosIndex = 0;

        int lastPos = 0;

        int nameCode;
        NPAttributes atts;

        for (eventTypeIndex = 0; eventTypeIndex < eventList.eventTypeArr.length; eventTypeIndex++) {
            {
                switch (eventList.eventTypeArr[eventTypeIndex]) {
                    case NPEventList.START_ELEMENT:
                        nameCode = eventList.nameCodeArr[nameCodeIndex++];
                        startElement(namePool, nameCode);
                        receiver.startContent();
                        break;
                    case NPEventList.START_ELEMENT_WITH_ATTS:
                        nameCode = eventList.nameCodeArr[nameCodeIndex++];
                        startElement(namePool, nameCode);
                        atts = eventList.attsArr[attsIndex++];
                        attributes(namePool, atts);
                        receiver.startContent();
                        break;
                    case NPEventList.END_ELEMENT:
                        receiver.endElement();
                        break;
                    case NPEventList.CHARACTERS:
                        final int pos = eventList.charPosArr[charPosIndex++];
                        final CharSlice slice = new CharSlice(eventList.charArr, lastPos, pos - lastPos);
                        receiver.characters(slice, NULL_LOCATION, ReceiverOptions.WHOLE_TEXT_NODE);
                        lastPos = pos;
                        break;
                }
            }
        }
    }

    private void startElement(final NPEventListNamePool namePool, final int nameCode) throws XPathException {
        final int code = mapCode(namePool, nameCode);
        receiver.startElement(new CodedName(code, EMPTY, pool), Untyped.getInstance(), NULL_LOCATION, ReceiverOptions.NAMESPACE_OK);
    }

    private void attributes(final NPEventListNamePool namePool, final NPAttributes atts) throws XPathException {
        for (int a = 0; a < atts.length; a++) {
            final int code = mapCode(namePool, atts.nameCode[a]);
            receiver.attribute(new CodedName(code, EMPTY, pool), BuiltInAtomicType.UNTYPED_ATOMIC, atts.value[a], NULL_LOCATION,
                    ReceiverOptions.NAMESPACE_OK);
        }
    }

    private int mapCode(final NPEventListNamePool namePool, final int nameCode) {
        return codeMap.computeIfAbsent(nameCode, k -> {
            final String uri = namePool.getURI(nameCode);
            final String localName = namePool.getLocalName(nameCode);
            final int code = pool.allocateFingerprint(uri, localName);
            codeMap.put(nameCode, code);
            return code;
        });
    }

    private static class NullLocation implements Location {
        @Override
        public String getSystemId() {
            return null;
        }

        @Override
        public String getPublicId() {
            return null;
        }

        @Override
        public int getLineNumber() {
            return 0;
        }

        @Override
        public int getColumnNumber() {
            return 0;
        }

        @Override
        public Location saveLocation() {
            return this;
        }
    }
}
