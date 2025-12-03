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

package stroom.pipeline.refdata.store;

import stroom.util.xml.XMLUtil;

import com.esotericsoftware.kryo.io.ByteBufferInputStream;
import com.sun.xml.fastinfoset.sax.SAXDocumentParser;
import org.xml.sax.InputSource;

import java.io.StringWriter;
import java.io.Writer;
import java.nio.ByteBuffer;

public class FastInfosetUtil {

    public static String byteBufferToString(final ByteBuffer byteBuffer) {
        try {
            final Writer writer = new StringWriter(1000);
            final SAXDocumentParser parser = new SAXDocumentParser();
            XMLUtil.prettyPrintXML(parser, new InputSource(new ByteBufferInputStream(byteBuffer.duplicate())), writer);
            return writer.toString();

        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }
}
