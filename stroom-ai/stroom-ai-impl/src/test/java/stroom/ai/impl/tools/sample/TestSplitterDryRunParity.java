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

package stroom.ai.impl.tools.sample;

import stroom.ai.impl.tools.draft.SplitterXml;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/// Found in the audit of 2026-11-26: the dry run's records (what the tools' conditions and checks read a data_splitter
/// input as) against Stroom's own Data Splitter parser running the converter the tools write, on edge-case samples.
class TestSplitterDryRunParity {

    private static final DataSplitterSchema SCHEMA = DataSplitterSchemas.held();
    private static final SplitterSpec CSV = SplitterSpec.builder("delimited").delimiter(",").header(true)
            .quote("\"").build();
    private static final SplitterSpec TSV = SplitterSpec.builder("delimited").delimiter("\t").header(true).build();
    private static final SplitterSpec KV = SplitterSpec.builder("key_value").delimiter(" ").quote("\"").build();

    static Stream<Arguments> theDryRunReadsAsStroomReads() {
        return Stream.of(
                Arguments.of("CSV, CRLF line ends", CSV, "a,b\r\n1,2\r\n3,4\r\n"),
                Arguments.of("CSV, empty cells", CSV, "a,b,c\n1,,3\n,,\n4,5,\n"),
                Arguments.of("CSV, quoted cell holding a new line", CSV, "a,b\n\"x\ny\",2\n3,4\n"),
                Arguments.of("CSV, quoted cell holding the delimiter and doubled quotes", CSV,
                        "a,b\n\"x, \"\"y\"\"\",2\n"),
                Arguments.of("CSV, quoted empty cell", CSV, "a,b\n\"\",2\n"),
                Arguments.of("CSV, spaces round a quoted cell", CSV, "a,b\n \"x\" ,2\n"),
                Arguments.of("CSV, a heading duplicated", CSV, "a,a,b\n1,2,3\n"),
                Arguments.of("CSV, a heading with spaces", CSV, "first name,b\nalice,2\n"),
                Arguments.of("CSV, fewer cells than headings", CSV, "a,b,c\n1,2\n"),
                Arguments.of("CSV, a quote inside an unquoted cell", CSV, "a,b\nsay \"hi\",2\n"),
                Arguments.of("TSV, trailing tab", TSV, "a\tb\n1\t\n"),
                Arguments.of("key=value, empty value", KV, "a= b=2\n"),
                Arguments.of("key=value, empty quoted value", KV, "a=\"\" b=2\n"),
                Arguments.of("key=value, a key given twice", KV, "a=1 a=2 b=3\n"),
                Arguments.of("key=value, CRLF", KV, "a=1 b=2\r\na=3 b=4\r\n"),
                Arguments.of("key=value, value holding =", KV, "a=x=y b=2\n"),
                Arguments.of("key=value, tab between pairs", KV, "a=1\tb=2\n"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void theDryRunReadsAsStroomReads(final String name, final SplitterSpec spec, final String sample) {
        final StroomSplitterRun.Result stroom = StroomSplitterRun.run(SCHEMA, SplitterXml.converter(spec),
                List.of(sample)).getFirst();
        assertThat(SplitterDryRun.dryRun(spec, sample).records()).as("the dry run's records, Stroom's errors %s",
                stroom.errors()).isEqualTo(stroom.records());
    }
}
