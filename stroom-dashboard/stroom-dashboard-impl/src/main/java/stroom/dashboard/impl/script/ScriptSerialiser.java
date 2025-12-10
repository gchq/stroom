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

package stroom.dashboard.impl.script;

import stroom.docstore.api.DocumentSerialiser2;
import stroom.docstore.api.Serialiser2;
import stroom.docstore.api.Serialiser2Factory;
import stroom.script.shared.ScriptDoc;
import stroom.util.string.EncodingUtil;

import jakarta.inject.Inject;

import java.io.IOException;
import java.util.Map;

public class ScriptSerialiser implements DocumentSerialiser2<ScriptDoc> {

    private static final String JS = "js";

    private final Serialiser2<ScriptDoc> delegate;

    @Inject
    public ScriptSerialiser(final Serialiser2Factory serialiser2Factory) {
        this.delegate = serialiser2Factory.createSerialiser(ScriptDoc.class);
    }

    @Override
    public ScriptDoc read(final Map<String, byte[]> data) throws IOException {
        final ScriptDoc document = delegate.read(data);

        final String js = EncodingUtil.asString(data.get(JS));
        if (js != null) {
            document.setData(js);
        }
        return document;
    }

    @Override
    public Map<String, byte[]> write(final ScriptDoc document) throws IOException {
        final String js = document.getData();
        document.setData(null);

        final Map<String, byte[]> data = delegate.write(document);

        if (js != null) {
            data.put(JS, EncodingUtil.asBytes(js));
            document.setData(js);
        }

        return data;
    }
}
