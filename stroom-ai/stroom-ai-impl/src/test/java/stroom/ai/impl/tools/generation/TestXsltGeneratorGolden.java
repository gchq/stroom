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

package stroom.ai.impl.tools.generation;

import stroom.ai.impl.tools.sample.PyJson;
import stroom.ai.impl.tools.validation.EventSchema;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/// The ported generator against the Stroom MCP server's own: each golden file under
/// `golden/generation/` is a mapping the server's tests generated (named after the test), or an evaluation case's
/// reference mapping, with what the server's `utils/xsltgen.generate` gave for it (problems, warnings, the events
/// summary) and the XSLT it wrote. The port must read the mapping to the same JSON (`model_dump`, compact and in
/// full) and write the same XSLT, to the byte.
///
/// The files were made by running the server's generator (`make_golden.py` over the calls recorded while its test
/// suite ran); they are not regenerated from the Java side.
class TestXsltGeneratorGolden {

    private static final String DIRECTORY = "/stroom/ai/impl/golden/generation";

    @TestFactory
    Stream<DynamicTest> theGeneratedXsltIsThePythonGeneratorsToTheByte() {
        return cases().map(json -> DynamicTest.dynamicTest(json.getFileName().toString(), () -> check(json)));
    }

    @SuppressWarnings("unchecked")
    private static void check(final Path json) throws IOException {
        final Map<String, Object> golden = (Map<String, Object>) PyJson.loads(Files.readString(json,
                StandardCharsets.UTF_8));
        final Map<String, Object> given = (Map<String, Object>) golden.get("mapping");
        final TranslationMapping mapping = TranslationMapping.fromJson(given);
        assertThat(PyJson.dumps(mapping.toJson(DumpMode.COMPACT))).isEqualTo(PyJson.dumps(given));
        if (golden.containsKey("full")) {
            assertThat(PyJson.dumps(mapping.toJson(DumpMode.ALL))).isEqualTo(PyJson.dumps(golden.get("full")));
        }
        final EventSchema schema = "3.5.2".equals(golden.get("schema"))
                ? GenerationFixtures.SCHEMA_352
                : GenerationFixtures.SCHEMA;
        final XsltGenerator.Generated generated = XsltGenerator.generate(mapping, schema,
                (String) golden.get("version"), Boolean.TRUE.equals(golden.get("mark_rules")));
        final Map<String, Object> result = new LinkedHashMap<>(generated.toMap());
        result.remove("xslt");
        // The one wording the port changes: XsltSafety (ported before) says "the tools" where the server said "the
        // server" of the functions it refuses.
        final String expected = PyJson.dumps(golden.get("result"), 1)
                .replace("the server doesn't run or save it", "the tools don't run or save it");
        assertThat(PyJson.dumps(result, 1)).isEqualTo(expected);
        final Path xslt = json.resolveSibling(json.getFileName().toString().replace(".json", ".xslt"));
        if (Files.exists(xslt)) {
            assertThat(generated.xslt()).isEqualTo(GenerationFixtures.asThePortWritesTimes(Files.readString(xslt,
                    StandardCharsets.UTF_8)));
        } else {
            assertThat(generated.xslt()).isNull();
        }
    }

    private static Stream<Path> cases() {
        try {
            final URL url = TestXsltGeneratorGolden.class.getResource(DIRECTORY);
            assertThat(url).isNotNull();
            final List<Path> files;
            try (final Stream<Path> listed = Files.list(Path.of(url.toURI()))) {
                files = listed.filter(path -> path.toString().endsWith(".json")).sorted().toList();
            }
            assertThat(files).hasSizeGreaterThan(200);
            return files.stream();
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        } catch (final URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }
}
