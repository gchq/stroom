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

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// A Data Splitter checked as Stroom's DSParser checks it before running it.
class TestDataSplitterSchema {

    private final DataSplitterSchema schema = DataSplitterSchemas.held();

    @Test
    void theConvertersTheToolsWriteAreValid() {
        for (final Map<String, Object> example : SplitterXml.EXAMPLES.values()) {
            final String converter = SplitterXml.converter(SplitterSpec.fromMap(example));

            assertThat(schema.check(converter).problems()).as(converter).isEmpty();
        }
    }

    @Test
    void elementsAndAttributesTheSchemaDoesntHaveAreErrorsWithTheirLine() {
        // Seen in testing: a hand-written converter naming its fields with <entry>, and group on the regex. Stroom's
        // parser ignores both, so its records had no fields.
        final String converter = DataSplitterSchema.ROOT + "\n"
                                 + "  <regex pattern=\"^(\\S+) (\\S+) (.*)$\" group=\"0\">\n"
                                 + "    <entry>ReceiveTime</entry>\n"
                                 + "    <entry>EventTime</entry>\n"
                                 + "    <entry>Body</entry>\n"
                                 + "  </regex>\n"
                                 + "</dataSplitter>";

        final DataSplitterSchema.Check check = schema.check(converter);

        assertThat(check.fatal()).isFalse();
        assertThat(check.problems())
                .anySatisfy(problem -> assertThat(problem).startsWith("line 2 ").contains("group"))
                .anySatisfy(problem -> assertThat(problem).startsWith("line 3 ").contains("entry"));
        // As Stroom's schema filter says it: no cvc code, no namespace in quotes.
        assertThat(check.problems()).noneSatisfy(problem -> assertThat(problem).contains("cvc-")
                .contains("\"data-splitter:3\":"));
    }

    @Test
    void aConverterWithNoNamespaceIsntRun() {
        final DataSplitterSchema.Check check = schema.check("<dataSplitter version=\"3.0\"><regex pattern=\".*\">"
                                                            + "<data value=\"$\"/></regex></dataSplitter>");

        assertThat(check.fatal()).isTrue();
        assertThat(check.problems()).singleElement().asString().contains("declares no namespace")
                .contains(DataSplitterSchema.ROOT);
    }

    @Test
    void aConverterInAnotherNamespaceIsntRun() {
        final DataSplitterSchema.Check check = schema.check("<dataSplitter xmlns=\"data-splitter:2\" "
                                                            + "version=\"3.0\"/>");

        assertThat(check.fatal()).isTrue();
        assertThat(check.problems()).singleElement().asString().contains("'data-splitter:2'");
    }

    @Test
    void aConverterWithNoSchemaLocationIsntRun() {
        final DataSplitterSchema.Check check = schema.check("<dataSplitter xmlns=\"data-splitter:3\" version=\"3.0\">"
                                                            + "<regex pattern=\".*\"><data value=\"$\"/></regex>"
                                                            + "</dataSplitter>");

        assertThat(check.fatal()).isTrue();
        assertThat(check.problems()).singleElement().asString().contains("no xsi:schemaLocation")
                .contains(DataSplitterSchema.ROOT);
    }

    @Test
    void withNoSchemaHeldNoDataSplitterRuns() {
        final DataSplitterSchema.Check check = DataSplitterSchemas.none().check(SplitterXml.converter(
                SplitterSpec.builder("delimited").delimiter(",").header(true).build()));

        assertThat(check.fatal()).isTrue();
        assertThat(check.problems()).containsExactly(DataSplitterSchema.NOT_HELD);
        assertThat(DataSplitterSchema.NOT_HELD).contains("data-splitter-v3.0.xsd").contains("content pack");
    }

    @Test
    void aSchemaThatDoesntCompileIsSaid() {
        final DataSplitterSchema.Check check = DataSplitterSchemas.of("<not a schema").check(SplitterXml.converter(
                SplitterSpec.builder("delimited").delimiter(",").header(true).build()));

        assertThat(check.fatal()).isTrue();
        assertThat(check.problems()).singleElement().asString().contains("doesn't compile");
    }

    @Test
    void aConverterThatIsntWellFormedIsntRun() {
        final DataSplitterSchema.Check check = schema.check("<dataSplitter");

        assertThat(check.fatal()).isTrue();
        assertThat(check.problems()).singleElement().asString().startsWith("The Data Splitter is not well-formed");
    }
}
