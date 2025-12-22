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

package stroom.state.impl.dao;

import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValNull;
import stroom.query.language.functions.ValString;
import stroom.util.xml.XMLUtil;

import com.esotericsoftware.kryo.io.ByteBufferInputStream;
import com.sun.xml.fastinfoset.sax.SAXDocumentParser;
import org.xml.sax.InputSource;

import java.io.StringWriter;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class ValUtil {

    private static final ValString STRING = ValString.create("String");
    private static final ValString FAST_INFOSET = ValString.create("Fast Infoset");

    public static Val getType(final byte valueType) {
        return switch (valueType) {
            case stroom.pipeline.refdata.store.StringValue.TYPE_ID -> STRING;
            case stroom.pipeline.refdata.store.FastInfosetValue.TYPE_ID -> FAST_INFOSET;
            default -> ValNull.INSTANCE;
        };
    }

    public static Val getValue(final byte valueType, final ByteBuffer byteBuffer) {
        return switch (valueType) {
            case stroom.pipeline.refdata.store.StringValue.TYPE_ID ->
                    ValString.create(convertString(byteBuffer));
            case stroom.pipeline.refdata.store.FastInfosetValue.TYPE_ID ->
                    ValString.create(convertFastInfoset(byteBuffer));
            default -> ValNull.INSTANCE;
        };
    }

    public static String getString(final byte valueType, final ByteBuffer byteBuffer) {
        return switch (valueType) {
            case stroom.pipeline.refdata.store.StringValue.TYPE_ID ->
                    convertString(byteBuffer);
            case stroom.pipeline.refdata.store.FastInfosetValue.TYPE_ID ->
                    convertFastInfoset(byteBuffer);
            default -> null;
        };
    }

    private static String convertString(final ByteBuffer byteBuffer) {
        return new String(byteBuffer.array(), StandardCharsets.UTF_8);
    }

    private static String convertFastInfoset(final ByteBuffer byteBuffer) {
        try {
            final Writer writer = new StringWriter(1000);
            final SAXDocumentParser parser = new SAXDocumentParser();
            XMLUtil.prettyPrintXML(parser, new InputSource(new ByteBufferInputStream(byteBuffer)), writer);
            return writer.toString();

        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }
}
