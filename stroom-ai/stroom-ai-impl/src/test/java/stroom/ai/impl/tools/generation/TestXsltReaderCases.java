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

import stroom.ai.impl.tools.sample.ToolJson;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/// The reader against its fixed results: `reader_cases.json` holds every read and rebuild of the reader's cases (the
/// XSLTs once each, in a table), with what each must give (a reading's digest; a rebuild's mapping, compact, and its
/// lists); and, per golden XSLT of the generator, digests of what reading it, rebuilding it with the mapping lost,
/// and rebuilding it with the mapping kept must give. They pin the reader's established behaviour.
class TestXsltReaderCases {

    private static final Map<String, Object> CASES = ReadingFixtures.json("reader_cases.json");
    private static final List<String> CODES = list(CASES.get("codes"));

    @TestFactory
    Stream<DynamicTest> everyRecordedReadGivesTheRecordedMapping() {
        final List<Map<String, Object>> reads = list(CASES.get("reads"));
        assertThat(reads).hasSizeGreaterThan(30);
        return IntStream.range(0, reads.size()).mapToObj(n -> {
            final Map<String, Object> c = reads.get(n);
            return DynamicTest.dynamicTest(n + " " + c.get("test"), () -> {
                final XsltReading reading = XsltReader.read(code(c.get("code")));
                assertThat(ReadingFixtures.digest(reading.toMap())).as(ToolJson.dumps(reading.toMap()))
                        .isEqualTo(c.get("reading"));
            });
        });
    }

    @TestFactory
    Stream<DynamicTest> everyRecordedRebuildGivesTheRecordedXslt() {
        final List<Map<String, Object>> rebuilds = list(CASES.get("rebuilds"));
        assertThat(rebuilds).hasSizeGreaterThan(45);
        return IntStream.range(0, rebuilds.size()).mapToObj(n -> {
            final Map<String, Object> c = rebuilds.get(n);
            return DynamicTest.dynamicTest(n + " " + c.get("test"), () -> {
                final TranslationMapping kept = c.get("kept") == null
                        ? null
                        : TranslationMapping.parse(c.get("kept"));
                final Map<String, String> imported = c.get("imported") == null
                        ? Map.of()
                        : map(c.get("imported"));
                final RebuiltMapping rebuilt = XsltReader.rebuild(code(c.get("code")), kept, code(c.get("kept_code")),
                        imported);
                assertThat(ToolJson.dumps(result(rebuilt, DumpMode.COMPACT), 1))
                        .isEqualTo(ToolJson.dumps(c.get("result"), 1));
            });
        });
    }

    @TestFactory
    Stream<DynamicTest> everyGoldenXsltReadsAndRebuildsToItsGolden() {
        final Map<String, List<String>> golden = map(CASES.get("golden"));
        final List<Path> files = ReadingFixtures.golden(".xslt");
        assertThat(files).hasSize(golden.size());
        return files.stream().map(path -> DynamicTest.dynamicTest(path.getFileName().toString(), () -> {
            final String code = ReadingFixtures.read(path);
            final Map<String, Object> json = GenerationFixtures.json(ReadingFixtures.read(
                    path.resolveSibling(path.getFileName().toString().replace(".xslt", ".json"))));
            final TranslationMapping mapping = TranslationMapping.parse(json.get("mapping"));
            final List<String> expected = golden.get(path.getFileName().toString());
            final Map<String, Object> reading = XsltReader.read(code).toMap();
            assertThat(ReadingFixtures.digest(reading)).as("reading %s", ToolJson.dumps(reading))
                    .isEqualTo(expected.get(0));
            final Map<String, Object> lost = result(XsltReader.rebuild(code, null, null), DumpMode.ALL);
            assertThat(ReadingFixtures.digest(lost)).as("lost %s", ToolJson.dumps(lost)).isEqualTo(expected.get(1));
            final Map<String, Object> kept = result(XsltReader.rebuild(code, mapping, code), DumpMode.ALL);
            assertThat(ReadingFixtures.digest(kept)).as("kept %s", ToolJson.dumps(kept)).isEqualTo(expected.get(2));
        }));
    }

    // A rebuild as the fixtures write one: the mapping (dumped so), and its lists.
    private static Map<String, Object> result(final RebuiltMapping rebuilt, final DumpMode mode) {
        final Map<String, Object> out = new LinkedHashMap<>();
        out.put("mapping", rebuilt.mapping() == null
                ? null
                : rebuilt.mapping().toJson(mode));
        out.put("reused", rebuilt.reused());
        out.put("new", rebuilt.added());
        out.put("removed", rebuilt.removed());
        out.put("raw", rebuilt.raw());
        out.put("imported_calls", rebuilt.importedCalls());
        out.put("notes", rebuilt.notes());
        out.put("problems", rebuilt.problems());
        return out;
    }

    private static String code(final Object index) {
        return index == null
                ? null
                : CODES.get(((Number) index).intValue());
    }

    @SuppressWarnings("unchecked")
    private static <T> List<T> list(final Object value) {
        return (List<T>) value;
    }

    @SuppressWarnings("unchecked")
    private static <T> Map<String, T> map(final Object value) {
        return (Map<String, T>) value;
    }
}
