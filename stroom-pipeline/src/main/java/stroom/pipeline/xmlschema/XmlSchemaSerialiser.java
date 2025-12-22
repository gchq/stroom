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

package stroom.pipeline.xmlschema;

import stroom.docstore.api.DocumentSerialiser2;
import stroom.docstore.api.Serialiser2;
import stroom.docstore.api.Serialiser2Factory;
import stroom.util.string.EncodingUtil;
import stroom.xmlschema.shared.XmlSchemaDoc;

import jakarta.inject.Inject;

import java.io.IOException;
import java.util.Map;

public class XmlSchemaSerialiser implements DocumentSerialiser2<XmlSchemaDoc> {

    private static final String XSD = "xsd";

    private final Serialiser2<XmlSchemaDoc> delegate;

    @Inject
    public XmlSchemaSerialiser(final Serialiser2Factory serialiser2Factory) {
        this.delegate = serialiser2Factory.createSerialiser(XmlSchemaDoc.class);
    }

    @Override
    public XmlSchemaDoc read(final Map<String, byte[]> data) throws IOException {
        final XmlSchemaDoc document = delegate.read(data);
        document.setData(EncodingUtil.asString(data.get(XSD)));
        return document;
    }

    @Override
    public Map<String, byte[]> write(final XmlSchemaDoc document) throws IOException {
        final String xsd = document.getData();
        document.setData(null);

        final Map<String, byte[]> data = delegate.write(document);
        if (xsd != null) {
            data.put(XSD, EncodingUtil.asBytes(xsd));
            document.setData(xsd);
        }

        return data;
    }
}
