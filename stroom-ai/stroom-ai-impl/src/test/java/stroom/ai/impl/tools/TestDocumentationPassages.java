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

package stroom.ai.impl.tools;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// Long reference documents read back a passage at a time, as the Stroom MCP server's tests have it.
class TestDocumentationPassages {

    private static final Map<String, Object> DOC = Map.of("type", "Documentation", "uuid", "d", "name",
            "Acme reference - events");

    @Test
    void aLongDocumentIsReadAPassageAtATime() {
        final String text = longDocument();

        assertThat(DocumentationPassages.outline(text)).startsWith("## Event 0", "## Event 10")
                .hasSize(DocumentationPassages.MAX_OUTLINE);

        // Case-insensitive.
        final Map<String, Object> found = DocumentationPassages.passages(DOC, text, "0xc000006a", 3);
        assertThat(found)
                .containsEntry("uuid", "d")
                .containsEntry("characters", text.length())
                .containsEntry("matches", 1)
                .doesNotContainKey("data")
                .doesNotContainKey("note");
        final List<?> passages = (List<?>) found.get("passages");
        assertThat(passages).singleElement().satisfies(passage -> {
            assertThat(((Map<?, ?>) passage).get("lines")).isEqualTo("12343-12349");
            assertThat((String) ((Map<?, ?>) passage).get("text")).contains("0xC000006A");
        });

        assertThat(DocumentationPassages.passages(DOC, text, "no such code", 3).get("note"))
                .asString().startsWith("Nothing mentions it");
    }

    @Test
    void severalTermsAndNearbyMatchesMakeOnePassage() {
        final String text = String.join("\n", "alpha", "x", "beta", "y", "z", "z", "z", "z", "gamma");

        final Map<String, Object> found = DocumentationPassages.passages(DOC, text, "ALPHA | beta|gamma", 1);

        assertThat(found).containsEntry("matches", 3);
        final List<Object> lines = ((List<?>) found.get("passages")).stream()
                .map(passage -> ((Map<?, ?>) passage).get("lines"))
                .map(Object.class::cast)
                .toList();
        assertThat(lines).containsExactly("1-4", "8-9");
    }

    @Test
    void passagesBeyondTheMostAreCountedInTheNote() {
        final List<String> lines = new ArrayList<>();
        for (int n = 0; n < 2_000; n++) {
            lines.add((n % 100 == 0
                    ? "MATCH "
                    : "") + "x".repeat(100));
        }

        final Map<String, Object> found = DocumentationPassages.passages(DOC, String.join("\n", lines), "match",
                DocumentationPassages.MAX_CONTEXT_LINES);

        assertThat(found.get("note")).asString().endsWith("more passages: narrow find, or lower contextLines");
    }

    private static String longDocument() {
        final List<String> lines = new ArrayList<>();
        for (int n = 0; n < 20_000; n++) {
            lines.add(n % 10 == 0
                    ? "## Event " + n
                    : "line " + n);
        }
        lines.set(12_345, "Event 4625: an account failed to log on (status 0xC000006A: bad password)");
        return String.join("\n", lines);
    }
}
