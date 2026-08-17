/*
 * Copyright 2016-2026 Crown Copyright
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

import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import java.io.StringReader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestXMLReaderPool {

    private static final String PLAIN_DOC = "<?xml version=\"1.0\"?><foo>hello</foo>";
    private static final String INVALID_DOC = "<foo>unclosed";

    // An XXE payload. If the DOCTYPE / external entity were processed the parser would try to read a local
    // file (file:///etc/hostname) rather than being rejected.
    private static final String XXE_DOC = """
            <?xml version="1.0"?>
            <!DOCTYPE foo [<!ENTITY xxe SYSTEM "file:///etc/hostname">]>
            <foo>&xxe;</foo>""";

    @Test
    void borrowedReaderIsReturnedAndReused() throws Exception {
        final XMLReaderPool pool = new XMLReaderPool(2);
        assertThat(pool.size()).isZero();

        final XMLReader first = pool.use(reader -> {
            // The reader is in use so it shouldn't also be available to borrow.
            assertThat(pool.size()).isZero();
            parse(reader, PLAIN_DOC);
            return reader;
        });

        assertThat(pool.size()).isEqualTo(1);

        final XMLReader second = pool.use(reader -> {
            parse(reader, PLAIN_DOC);
            return reader;
        });

        assertThat(second).isSameAs(first);
        assertThat(pool.size()).isEqualTo(1);
    }

    @Test
    void concurrentUseCreatesAnotherReader() throws Exception {
        final XMLReaderPool pool = new XMLReaderPool(2);

        pool.use(outer -> pool.use(inner -> {
            assertThat(inner).isNotSameAs(outer);
            return null;
        }));

        // Both readers were returned.
        assertThat(pool.size()).isEqualTo(2);
    }

    @Test
    void poolIsBounded() throws Exception {
        final XMLReaderPool pool = new XMLReaderPool(1);

        pool.use(outer -> pool.use(inner -> null));

        // The pool is full so the second reader was discarded rather than growing the pool.
        assertThat(pool.size()).isEqualTo(1);
    }

    @Test
    void failedReaderIsNotReturnedToThePool() {
        final XMLReaderPool pool = new XMLReaderPool(2);

        assertThatThrownBy(() -> pool.use(reader -> {
            parse(reader, INVALID_DOC);
            return null;
        })).isInstanceOf(SAXException.class);

        assertThat(pool.size()).isZero();
    }

    @Test
    void pooledReadersKeepTheirHardening() throws Exception {
        final XMLReaderPool pool = new XMLReaderPool(2);

        // Use the reader once so that the next borrow gets the same, already used, reader back.
        pool.use(reader -> {
            parse(reader, PLAIN_DOC);
            return null;
        });

        assertThatThrownBy(() -> pool.use(reader -> {
            parse(reader, XXE_DOC);
            return null;
        })).isInstanceOf(SAXException.class);
    }

    @Test
    void clearDiscardsPooledReaders() throws Exception {
        final XMLReaderPool pool = new XMLReaderPool(2);
        pool.use(reader -> null);
        assertThat(pool.size()).isEqualTo(1);

        pool.clear();
        assertThat(pool.size()).isZero();
    }

    private static void parse(final XMLReader reader, final String xml) throws Exception {
        reader.setContentHandler(new DefaultHandler());
        reader.parse(new InputSource(new StringReader(xml)));
    }
}
