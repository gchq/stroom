/*
 * Copyright 2018 Crown Copyright
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
 *
 */

package stroom.refdata.lmdb.eval;

import com.sun.xml.fastinfoset.sax.SAXDocumentParser;
import com.sun.xml.fastinfoset.sax.SAXDocumentSerializer;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import java.io.ByteArrayOutputStream;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

public class TestSaxFastInfoset {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestSaxFastInfoset.class);

    @Test
    public void testSaxFastInfosetSerializer() {

        Deflater deflater = new Deflater(9);
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        DeflaterOutputStream deflaterOutputStream = new DeflaterOutputStream(byteArrayOutputStream, deflater);
        SAXDocumentSerializer saxDocumentSerializer = new SAXDocumentSerializer();
        saxDocumentSerializer.setOutputStream(deflaterOutputStream);

        for (int i = 0; i < 100; i++) {
            try {
                saxDocumentSerializer.startDocument();
                saxDocumentSerializer.startPrefixMapping("t", "testuri");
                saxDocumentSerializer.startElement("testuri", "test", "test", null);
                String str = "testChars" + i;
                saxDocumentSerializer.characters(str.toCharArray(), 0, str.length());
                saxDocumentSerializer.endElement("testuri", "test", "test");
                saxDocumentSerializer.endDocument();
            } catch (final SAXException e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        }


        byte[] bytes = byteArrayOutputStream.toByteArray();

        LOGGER.info("bytes.length FastInfoset {}", bytes.length);

        SAXDocumentParser saxDocumentParser = new SAXDocumentParser();
//        saxDocumentParser.


    }
}
