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

package stroom.ai.impl.tools.indexing;

import stroom.ai.impl.tools.sample.PyJson;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/// The ported indexing XSLT generator against the Stroom MCP server's own: each golden pair under
/// `golden/indexing/` is a plan (as `model_dump` wrote it) and the XSLT the server's `FieldPlan.xslt` wrote for it,
/// recorded while the server's test suite ran (named after the test), and from crafted plans (styles, layouts,
/// shared templates, drop_when, discovery inputs). The port must read the plan back to the same JSON and write the
/// same XSLT, to the byte.
///
/// The files were made by running the server's code (the recorder and `make_cases.py` of this port); they are not
/// regenerated from the Java side.
class TestFieldPlanGolden {

    private static final String DIRECTORY = "/stroom/ai/impl/golden/indexing";

    @TestFactory
    Stream<DynamicTest> theIndexingXsltIsThePythonGeneratorsToTheByte() {
        return cases().map(json -> DynamicTest.dynamicTest(json.getFileName().toString(), () -> check(json)));
    }

    @SuppressWarnings("unchecked")
    private static void check(final Path json) throws IOException {
        final Map<String, Object> golden = IndexingFixtures.json(Files.readString(json, StandardCharsets.UTF_8));
        final Map<String, Object> given = (Map<String, Object>) golden.get("plan");
        final FieldPlan plan = FieldPlan.fromJson(given);
        assertThat(PyJson.dumps(plan.toJson())).isEqualTo(PyJson.dumps(given));
        final Path xslt = json.resolveSibling(json.getFileName().toString().replace(".json", ".xslt"));
        assertThat(plan.xslt((String) golden.get("version"))).isEqualTo(Files.readString(xslt,
                StandardCharsets.UTF_8));
    }

    private static Stream<Path> cases() {
        try {
            final URL url = TestFieldPlanGolden.class.getResource(DIRECTORY);
            assertThat(url).isNotNull();
            final List<Path> files;
            try (final Stream<Path> listed = Files.list(Path.of(url.toURI()))) {
                files = listed.filter(path -> path.toString().endsWith(".json")).sorted().toList();
            }
            assertThat(files).hasSizeGreaterThan(100);
            return files.stream();
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        } catch (final URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }
}
