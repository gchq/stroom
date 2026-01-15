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

package stroom.pipeline.xslt;

import stroom.docstore.api.DocumentSerialiser2;
import stroom.docstore.api.Serialiser2;
import stroom.docstore.api.Serialiser2Factory;
import stroom.pipeline.shared.XsltDoc;
import stroom.util.string.EncodingUtil;

import jakarta.inject.Inject;

import java.io.IOException;
import java.util.Map;

public class XsltSerialiser implements DocumentSerialiser2<XsltDoc> {

    private static final String XSL = "xsl";

    private final Serialiser2<XsltDoc> delegate;

    @Inject
    public XsltSerialiser(final Serialiser2Factory serialiser2Factory) {
        this.delegate = serialiser2Factory.createSerialiser(XsltDoc.class);
    }

    @Override
    public XsltDoc read(final Map<String, byte[]> data) throws IOException {
        final XsltDoc document = delegate.read(data);
        document.setData(EncodingUtil.asString(data.get(XSL)));
        return document;
    }

    @Override
    public Map<String, byte[]> write(final XsltDoc document) throws IOException {
        final String xsl = document.getData();
        document.setData(null);

        final Map<String, byte[]> data = delegate.write(document);
        if (xsl != null) {
            data.put(XSL, EncodingUtil.asBytes(xsl));
            document.setData(xsl);
        }

        return data;
    }
}
