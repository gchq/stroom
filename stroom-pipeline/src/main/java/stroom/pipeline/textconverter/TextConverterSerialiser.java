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

package stroom.pipeline.textconverter;

import stroom.docstore.api.DocumentSerialiser2;
import stroom.docstore.api.Serialiser2;
import stroom.docstore.api.Serialiser2Factory;
import stroom.pipeline.shared.TextConverterDoc;
import stroom.util.string.EncodingUtil;

import jakarta.inject.Inject;

import java.io.IOException;
import java.util.Map;

public class TextConverterSerialiser implements DocumentSerialiser2<TextConverterDoc> {

    private static final String XML = "xml";

    private final Serialiser2<TextConverterDoc> delegate;

    @Inject
    public TextConverterSerialiser(final Serialiser2Factory serialiser2Factory) {
        this.delegate = serialiser2Factory.createSerialiser(TextConverterDoc.class);
    }

    @Override
    public TextConverterDoc read(final Map<String, byte[]> data) throws IOException {
        final TextConverterDoc document = delegate.read(data);
        document.setData(EncodingUtil.asString(data.get(XML)));
        return document;
    }

    @Override
    public Map<String, byte[]> write(final TextConverterDoc document) throws IOException {
        final String xml = document.getData();
        document.setData(null);

        final Map<String, byte[]> map = delegate.write(document);
        if (xml != null) {
            map.put(XML, EncodingUtil.asBytes(xml));
            document.setData(xml);
        }

        return map;
    }
}
