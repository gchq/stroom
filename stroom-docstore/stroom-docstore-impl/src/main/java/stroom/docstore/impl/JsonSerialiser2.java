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

package stroom.docstore.impl;

import stroom.docstore.api.Serialiser2;
import stroom.util.json.JsonUtil;
import stroom.util.string.EncodingUtil;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;

public class JsonSerialiser2<D> implements Serialiser2<D> {

    private static final String META = "meta";

    private final Class<D> clazz;
    private final ObjectMapper mapper;

    public JsonSerialiser2(final Class<D> clazz) {
        this.clazz = clazz;
        this.mapper = JsonUtil.getMapper();
    }

    @Override
    public D read(final Map<String, byte[]> data) throws IOException {
        final byte[] meta = data.get(META);
        return read(meta);
    }

    @Override
    public D read(final byte[] data) throws IOException {
        return mapper.readValue(new StringReader(EncodingUtil.asString(data)), clazz);
    }

    @Override
    public Map<String, byte[]> write(final D document) throws IOException {
        final StringWriter stringWriter = new StringWriter();
        write(stringWriter, document);
        final Map<String, byte[]> data = new HashMap<>();
        data.put(META, EncodingUtil.asBytes(stringWriter.toString()));
        return data;
    }

    @Override
    public void write(final Writer writer, final D document) throws IOException {
        mapper.writeValue(writer, document);
    }
}
