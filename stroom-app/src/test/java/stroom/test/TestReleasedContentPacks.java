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

package stroom.test;

import stroom.test.common.util.test.ContentPackZip;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestReleasedContentPacks {

    private static final String DEFINITION = """
            {
              "contentPacks" : [ {
                "url" : "https://example.com/core-xml-schemas-v2.0.zip",
                "name" : "core-xml-schemas",
                "version" : "2.0"
              }, {
                "url" : "https://example.com/stroom-visualisations-v8.0-beta.3.zip",
                "name" : "stroom-visualisations",
                "version" : "8.0-beta.3"
              } ]
            }
            """;

    @Test
    void findsAPackByName(@TempDir final Path dir) throws IOException {
        final Path definition = Files.writeString(dir.resolve("content-packs.json"), DEFINITION);

        final ContentPackZip pack = ReleasedContentPacks.find(definition, "stroom-visualisations");

        assertThat(pack.getUrl()).isEqualTo("https://example.com/stroom-visualisations-v8.0-beta.3.zip");
        assertThat(pack.getVersion()).isEqualTo("8.0-beta.3");
        assertThat(pack.toFileName()).isEqualTo("stroom-visualisations-v8.0-beta.3.zip");
    }

    @Test
    void refusesANameNotListed(@TempDir final Path dir) throws IOException {
        final Path definition = Files.writeString(dir.resolve("content-packs.json"), DEFINITION);

        assertThatThrownBy(() -> ReleasedContentPacks.find(definition, "stroom-visualisation"))
                .hasMessageContaining("lists no content pack named 'stroom-visualisation'");
    }

    @Test
    void refusesAMissingFile(@TempDir final Path dir) {
        assertThatThrownBy(() -> ReleasedContentPacks.find(dir.resolve("content-packs.json"), "stroom-visualisations"))
                .hasMessageContaining("No content pack definition file at");
    }

    @Test
    void refusesAFileThatIsNotADefinition(@TempDir final Path dir) throws IOException {
        final Path definition = Files.writeString(dir.resolve("content-packs.json"), "not json");

        assertThatThrownBy(() -> ReleasedContentPacks.find(definition, "stroom-visualisations"))
                .hasMessageContaining("Unable to read the content pack definition file");
    }

    @Test
    void anEmptyDefinitionListsNoPacks(@TempDir final Path dir) throws IOException {
        final Path definition = Files.writeString(dir.resolve("content-packs.json"), "{}");

        assertThatThrownBy(() -> ReleasedContentPacks.find(definition, "stroom-visualisations"))
                .hasMessageContaining("lists no content pack named");
    }

    // The repository's own definition names the visualisations' pack that SetupSampleData imports
    // (the tests run in stroom-app, below the repository's root)
    @Test
    void theRepositoryListsTheVisualisations() {
        final ContentPackZip pack = ReleasedContentPacks.find(
                Path.of("..", ReleasedContentPacks.DEFINITION_FILE), "stroom-visualisations");

        assertThat(pack.getUrl()).startsWith("https://github.com/gchq/stroom-visualisations-dev/releases/download/");
    }
}
