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

package stroom.util.xml;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import java.io.Reader;
import javax.xml.parsers.SAXParserFactory;

@Disabled
class TestSAXParserFactoryFactory {

    @Test
    void testExceedingTotalEntities() throws Exception {
        // Set this to true to test limits.
        SAXParserSettings.setSecureProcessingEnabled(false);

        final SAXParserFactory factory = SAXParserFactoryFactory.newInstance();
        final XMLReader xmlReader = factory.newSAXParser().getXMLReader();
        xmlReader.parse(new InputSource(new XmlGenerator()));
    }

    private static class XmlGenerator extends Reader {

        private static final int MAX_COUNT = 1000000;
        private static final int MAX_DEPTH = 1000;
        private static final boolean UNIQUE_NAMES = false;

        private final RingBuffer buf = new RingBuffer(1000);
        private int count;
        private int depth;
        private boolean incrementing = true;
        private boolean done;

        XmlGenerator() {
            buf.append("<?xml version=\"1.1\" encoding=\"UTF-8\"?>\n<root>\n");
        }

        @Override
        public int read(final char[] cbuf, final int off, final int len) {
            if (buf.length() == 0 && !done) {
                buf.append("<");
                if (!incrementing) {
                    buf.append("/");
                }
                buf.append("element");
                if (UNIQUE_NAMES) {
                    buf.append("_");
                    buf.append(count);
                    buf.append("-");
                    buf.append(depth);
                }
                buf.append(">this &amp; that\n");

                if (incrementing) {
                    if (depth == MAX_DEPTH) {
                        incrementing = false;
                    } else {
                        depth++;
                    }
                } else {
                    if (depth == 0) {
                        incrementing = true;
                        count++;

                        if (count == MAX_COUNT) {
                            buf.append("</root>");
                            done = true;
                        }
                    } else {
                        depth--;
                    }
                }
            }

            final int l = Math.min(len, buf.length());
            for (int i = 0; i < l; i++) {
                cbuf[off + i] = buf.charAt(i);
            }

            buf.move(l);

            if (l == 0 && done) {
                return -1;
            }

            return l;
        }

        @Override
        public void close() {
        }
    }
}
