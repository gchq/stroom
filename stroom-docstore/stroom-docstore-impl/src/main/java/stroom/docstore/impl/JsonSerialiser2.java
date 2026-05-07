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
import stroom.importexport.api.ByteArrayImportExportAsset;
import stroom.importexport.api.ImportExportAsset;
import stroom.importexport.api.ImportExportDocument;
import stroom.util.json.JsonUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.string.EncodingUtil;

import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

public class JsonSerialiser2<D> implements Serialiser2<D> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(JsonSerialiser2.class);

    private static final String META = "meta";

    private final Class<D> clazz;
    private final JsonMapper mapper;

    static {
        // Make sure EncodingUtil is using the same charset as writeValueAsBytes.
        // This is because we are using methods like jsonMapper.readValue(jsonBytes, clazz)
        // which use UTF-8 under the hood. If EncodingUtil ever changes its charset then
        // we need to change how we use Jackson
        if (!StandardCharsets.UTF_8.equals(EncodingUtil.CHARSET)) {
            throw new IllegalStateException("Expecting EncodingUtil to use UTF8");
        }
    }

    public JsonSerialiser2(final Class<D> clazz) {
        this.clazz = clazz;
        this.mapper = JsonUtil.getMapper();
    }

    @Override
    public D read(final ImportExportDocument importExportDocument) throws IOException {
        return read(importExportDocument.getExtAsset(META));
    }

    @Override
    public D read(final ImportExportAsset asset) throws IOException {
        if (asset != null) {
            final byte[] data = asset.getInputData();
            if (data != null) {
                final D document = mapper.readValue(data, clazz);
                LOGGER.trace(() -> LogUtil.message("read() - document: {}, json:\n{}",
                        document, EncodingUtil.asString(data)));
                return document;
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    @Override
    public ImportExportDocument write(final D document) throws IOException {
        final byte[] jsonBytes = writeAsBytes(document);
        final ImportExportDocument importExportDocument = new ImportExportDocument();
        importExportDocument.addExtAsset(new ByteArrayImportExportAsset(META, jsonBytes));
        LOGGER.trace(() -> LogUtil.message("write() - document: {}, json:\n{}",
                document, EncodingUtil.asString(jsonBytes)));
        return importExportDocument;
    }

    @Override
    public void write(final Writer writer, final D document) throws IOException {
        mapper.writeValue(writer, document);
    }

    @Override
    public String writeAsString(final D document) {
        final String json = mapper.writeValueAsString(document);
        LOGGER.trace("writeAsString() - document: {}, json:\n{}", document, json);
        return json;
    }

    @Override
    public byte[] writeAsBytes(final D document) {
        // UTF-8 bytes
        final byte[] jsonBytes = mapper.writeValueAsBytes(document);
        LOGGER.trace(() -> LogUtil.message("writeAsBytes() - document: {}, json:\n{}",
                document, EncodingUtil.asString(jsonBytes)));
        return jsonBytes;
    }
}
