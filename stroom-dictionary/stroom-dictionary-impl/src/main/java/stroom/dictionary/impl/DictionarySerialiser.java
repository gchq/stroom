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

package stroom.dictionary.impl;

import stroom.dictionary.shared.DictionaryDoc;
import stroom.docstore.api.DocumentSerialiser2;
import stroom.docstore.api.Serialiser2;
import stroom.docstore.api.Serialiser2Factory;
import stroom.util.string.EncodingUtil;

import jakarta.inject.Inject;

import java.io.IOException;
import java.util.Map;

public class DictionarySerialiser implements DocumentSerialiser2<DictionaryDoc> {

    private static final String TEXT = "txt";

    private final Serialiser2<DictionaryDoc> delegate;

    @Inject
    public DictionarySerialiser(final Serialiser2Factory serialiser2Factory) {
        this.delegate = serialiser2Factory.createSerialiser(DictionaryDoc.class);
    }

    @Override
    public DictionaryDoc read(final Map<String, byte[]> data) throws IOException {
        final DictionaryDoc document = delegate.read(data);
        document.setData(EncodingUtil.asString(data.get(TEXT)));
        return document;
    }

    @Override
    public Map<String, byte[]> write(final DictionaryDoc document) throws IOException {
        final String text = document.getData();
        document.setData(null);

        final Map<String, byte[]> data = delegate.write(document);
        if (text != null) {
            data.put(TEXT, EncodingUtil.asBytes(text));
            document.setData(text);
        }

        return data;
    }
}
