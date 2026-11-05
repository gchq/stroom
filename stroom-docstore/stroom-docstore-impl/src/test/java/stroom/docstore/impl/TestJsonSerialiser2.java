/*
 * Copyright 2026 Crown Copyright
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

import stroom.dictionary.shared.DictionaryDoc;
import stroom.docref.DocRef;
import stroom.docstore.shared.DocDataType;
import stroom.importexport.api.ByteArrayImportExportAsset;
import stroom.importexport.api.ImportExportAsset;
import stroom.importexport.api.ImportExportDocument;
import stroom.util.json.JsonUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.string.EncodingUtil;

import org.assertj.core.api.Assertions;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TestJsonSerialiser2 {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestJsonSerialiser2.class);

    @Test
    void testSerialise() throws IOException {
        final DictionaryDoc doc = DictionaryDoc.builder()
                .uuid(UUID.randomUUID().toString())
                .name("test")
                .version(UUID.randomUUID().toString())
                .stampAudit("user1")
                .description("This is my doc")
                .imports(List.of(
                        DictionaryDoc.buildDocRef()
                                .name("import 1")
                                .randomUuid()
                                .build(),
                        DictionaryDoc.buildDocRef()
                                .name("import 2")
                                .randomUuid()
                                .build()))
                .data("This is my data")
                .build();

        final JsonSerialiser2<DictionaryDoc> serialiser2 = new JsonSerialiser2<>(DictionaryDoc.class);

        final ImportExportDocument importExportDocument = serialiser2.write(doc);
        final String json = serialiser2.writeAsString(doc);
        LOGGER.info("json:\n{}", json);
        final byte[] bytes = serialiser2.writeAsBytes(doc);
        final StringWriter stringWriter = new StringWriter();
        serialiser2.write(stringWriter, doc);

        // Make sure all the ser methods are producing the same thing
        assertThat(importExportDocument.getExtAsset("meta")
                .getInputData())
                .isEqualTo(bytes);
        assertThat(json.getBytes(StandardCharsets.UTF_8))
                .isEqualTo(bytes);
        assertThat(stringWriter.toString().getBytes(StandardCharsets.UTF_8))
                .isEqualTo(bytes);
        assertThat(stringWriter.toString())
                .isEqualTo(json);
        assertThat(new String(bytes, StandardCharsets.UTF_8))
                .isEqualTo(json);

        // Make sure all the deser methods are producing the same doc
        final DictionaryDoc doc2 = JsonUtil.getMapper().readValue(bytes, DictionaryDoc.class);
        assertThat(doc2)
                .isEqualTo(doc);
        final DictionaryDoc doc3 = serialiser2.read(new ByteArrayImportExportAsset("meta", DocDataType.JSON, bytes));
        assertThat(doc3)
                .isEqualTo(doc);
        final ImportExportDocument importExportDocument2 = new ImportExportDocument();
        importExportDocument2.addExtAsset(new ByteArrayImportExportAsset("meta", DocDataType.JSON, bytes));
        final DictionaryDoc doc4 = serialiser2.read(importExportDocument2);
        assertThat(doc4)
                .isEqualTo(doc);
    }

    @Test
    void testInvalidDocRef_missingUuid() throws IOException {
        final String json = """
                {
                  "type" : "Dictionary",
                  "uuid" : "b951f5ea-7946-4245-9302-2b1261b3a2e6",
                  "name" : "test",
                  "version" : "f275d1b2-3888-47a1-85d8-e9845d70303b",
                  "createTimeMs" : 1793715413233,
                  "updateTimeMs" : 1793715413233,
                  "createUser" : "user1",
                  "updateUser" : "user1",
                  "description" : "This is my doc",
                  "data" : "This is my data",
                  "imports" : [ {
                    "type" : "Dictionary",
                    "name" : "import 1"
                  }, {
                    "type" : "Dictionary",
                    "uuid" : "ea40cc6b-feb7-422e-9898-6ffc56bf5378",
                    "name" : "import 2"
                  } ]
                }
                """;
        final JsonSerialiser2<DictionaryDoc> serialiser2 = new JsonSerialiser2<>(DictionaryDoc.class);
        final ImportExportAsset asset = new ByteArrayImportExportAsset(
                "json",
                DocDataType.JSON,
                EncodingUtil.asBytes(json));

        Assertions.assertThatThrownBy(
                        () -> {
                            serialiser2.read(asset);
                        })
                .hasMessageContaining("UUID")
                .isInstanceOf(RuntimeException.class)
                .extracting(Throwable::getCause,
                        Assertions.as(InstanceOfAssertFactories.THROWABLE))
                .hasCauseInstanceOf(DocRef.InvalidDocRefException.class);
    }

    @Test
    void testInvalidDocRef_missingType() {
        final String json = """
                {
                  "type" : "Dictionary",
                  "uuid" : "b951f5ea-7946-4245-9302-2b1261b3a2e6",
                  "name" : "test",
                  "version" : "f275d1b2-3888-47a1-85d8-e9845d70303b",
                  "createTimeMs" : 1793715413233,
                  "updateTimeMs" : 1793715413233,
                  "createUser" : "user1",
                  "updateUser" : "user1",
                  "description" : "This is my doc",
                  "data" : "This is my data",
                  "imports" : [ {
                    "uuid" : "566c2329-8796-40d2-830d-041113d50f00",
                    "name" : "import 1"
                  }, {
                    "type" : "Dictionary",
                    "uuid" : "ea40cc6b-feb7-422e-9898-6ffc56bf5378",
                    "name" : "import 2"
                  } ]
                }
                """;
        final JsonSerialiser2<DictionaryDoc> serialiser2 = new JsonSerialiser2<>(DictionaryDoc.class);
        final ImportExportAsset asset = new ByteArrayImportExportAsset(
                "json",
                DocDataType.JSON,
                EncodingUtil.asBytes(json));

        Assertions.assertThatThrownBy(
                        () -> {
                            serialiser2.read(asset);
                        })
                .hasMessageContaining("type")
                .isInstanceOf(RuntimeException.class)
                .extracting(Throwable::getCause,
                        Assertions.as(InstanceOfAssertFactories.THROWABLE))
                .hasCauseInstanceOf(DocRef.InvalidDocRefException.class);
    }
}
