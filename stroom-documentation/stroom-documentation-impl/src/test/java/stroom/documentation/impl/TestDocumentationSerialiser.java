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

package stroom.documentation.impl;

import stroom.docstore.impl.Serialiser2FactoryImpl;
import stroom.documentation.shared.DocumentationDoc;
import stroom.importexport.api.ImportExportDocument;
import stroom.util.json.JsonUtil;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// The form a Documentation document is imported, exported and synced to Git in: its JSON, and its text beside
/// it as a `.txt` asset of its own.
class TestDocumentationSerialiser {

    private static final String TEXT = "txt";
    private static final String META = "meta";

    private final DocumentationSerialiser serialiser = new DocumentationSerialiser(new Serialiser2FactoryImpl());

    @ParameterizedTest
    @ValueSource(strings = {
            "# Notes\n\nPlain text, a `code span` and a | in a table.\n",
            "Ünïcödé, 日本語 and an emoji 😀",
            "Windows line endings\r\nare kept\r\n",
            ""})
    void theTextIsKeptBesideTheJsonAndReadBackAsItWas(final String text) throws IOException {
        final ImportExportDocument written = serialiser.write(doc(text));

        assertThat(written.containsExtAssetWithKey(TEXT)).isTrue();
        assertThat(new String(written.getExtAssetData(TEXT), StandardCharsets.UTF_8)).isEqualTo(text);
        assertThat(json(written)).as("the JSON holds no text").doesNotContainKey("data");

        final DocumentationDoc read = serialiser.read(written);
        assertThat(read.getData()).isEqualTo(text);
        assertThat(read.getUuid()).isEqualTo("doc-uuid");
        assertThat(read.getName()).isEqualTo("Acme VPN");
    }

    @Test
    void aLargeDocumentIsReadBackWhole() throws IOException {
        final String text = "| Field | Example |\n| --- | --- |\n" + "| a.long.field.name | 😀 value |\n".repeat(60_000);

        assertThat(serialiser.read(serialiser.write(doc(text))).getData()).isEqualTo(text);
    }

    @Test
    void aDocumentWithNoTextHasNoTextAsset() throws IOException {
        final ImportExportDocument written = serialiser.write(doc(null));

        assertThat(written.containsExtAssetWithKey(TEXT)).isFalse();
        assertThat(written.containsExtAssetWithKey(META)).isTrue();
        assertThat(serialiser.read(written).getData()).isNull();
    }

    @Test
    void anImportOfTheJsonAloneIsReadWithNoText() throws IOException {
        // E.g. an export whose .txt file was left out.
        final ImportExportDocument written = serialiser.write(doc("Some text"));
        written.removeExtAsset(TEXT);

        final DocumentationDoc read = serialiser.read(written);

        assertThat(read.getData()).isNull();
        assertThat(read.getName()).isEqualTo("Acme VPN");
    }

    private static DocumentationDoc doc(final String text) {
        return DocumentationDoc.builder()
                .uuid("doc-uuid")
                .name("Acme VPN")
                .data(text)
                .build();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> json(final ImportExportDocument written) throws IOException {
        return JsonUtil.readValue(new String(written.getExtAssetData(META), StandardCharsets.UTF_8), Map.class);
    }
}
