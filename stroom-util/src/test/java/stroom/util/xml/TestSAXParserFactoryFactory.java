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
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import java.io.Reader;
import java.io.StringReader;
import javax.xml.parsers.SAXParserFactory;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestSAXParserFactoryFactory {

    // An XXE payload. If the DOCTYPE / external entity were processed the parser would try to read a local
    // file (file:///etc/hostname) rather than being rejected.
    private static final String XXE_DOC = """
            <?xml version="1.0"?>
            <!DOCTYPE foo [<!ENTITY xxe SYSTEM "file:///etc/hostname">]>
            <foo>&xxe;</foo>""";

    private static final String PLAIN_DOC = "<?xml version=\"1.0\"?><foo>hello</foo>";

    @Test
    void secureByDefaultRejectsDoctypeXxe() {
        // Default settings (disableExternalEntities=true) disallow the DOCTYPE outright, so the external
        // entity is never resolved.
        assertThatThrownBy(() -> parse(XXE_DOC))
                .isInstanceOf(SAXException.class);
    }

    @Test
    void plainXmlStillParses() {
        assertThatCode(() -> parse(PLAIN_DOC))
                .doesNotThrowAnyException();
    }

    @Test
    void hardeningCanBeDisabledViaSettings() {
        try {
            SAXParserSettings.setExternalEntitiesDisabled(false);
            // With the hardening opted out, a DOCTYPE is no longer rejected. Use an INTERNAL entity so the
            // test never touches the filesystem.
            final String internalEntityDoc = """
                    <?xml version="1.0"?>
                    <!DOCTYPE foo [<!ENTITY e "hi">]>
                    <foo>&e;</foo>""";
            assertThatCode(() -> parse(internalEntityDoc))
                    .doesNotThrowAnyException();
        } finally {
            SAXParserSettings.setExternalEntitiesDisabled(true);
        }
    }

    private void parse(final String xml) throws Exception {
        final SAXParserFactory factory = SAXParserFactoryFactory.newInstance();
        final XMLReader xmlReader = factory.newSAXParser().getXMLReader();
        xmlReader.setContentHandler(new DefaultHandler());
        xmlReader.parse(new InputSource(new StringReader(xml)));
    }

    @Disabled("Manual entity-limit stress test")
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
