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

package stroom.ai.impl.extraction;

import stroom.ai.impl.workspace.WorkspaceException;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestExtractionField {

    @Test
    void fieldsAreReadWithTheirTypesDefaultingToText() {
        final List<ExtractionField> fields = ExtractionField.parse(List.of(
                Map.of("name", "Organisation", "xpath", " EventSource/User/Organisation "),
                Map.of("name", "Bytes", "xpath", "number(Meta/Size)", "type", "Number")));

        assertThat(fields).containsExactly(
                new ExtractionField("Organisation", "EventSource/User/Organisation", "text"),
                new ExtractionField("Bytes", "number(Meta/Size)", "number"));
    }

    @Test
    void namesTheExtractionGivesAnywayOrTwiceAreRefused() {
        assertThat(ExtractionField.problems(List.of(
                new ExtractionField("StreamId", "@StreamId", null),
                new ExtractionField("User", "EventSource/User/Id", null),
                new ExtractionField("user", "EventSource/User/Name", null),
                new ExtractionField("", "Id", null),
                new ExtractionField("Bad<name>", "Id", null),
                new ExtractionField("When", "EventTime/TimeCreated", "time"))))
                .containsExactly(
                        "'StreamId' is given by every extraction, from the ids of the event; leave it out.",
                        "'user' is given more than once.",
                        "A field has no name.",
                        "'Bad<name>' isn't a name a field can have: letters, digits, spaces and _ . ( ) -, starting "
                        + "with a letter, up to 100 characters.",
                        "'When' has type 'time': give one of text, number, date.");
    }

    @Test
    void anXPathMayUseOnlyPlainStringAndNumberFunctions() {
        // It runs in Stroom's own engine on every search, where extension functions and reading other documents
        // are available.
        assertThat(ExtractionField.xpathProblem("EventSource/User/Id")).isNull();
        assertThat(ExtractionField.xpathProblem("@Id")).isNull();
        assertThat(ExtractionField.xpathProblem("upper-case(normalize-space(EventDetail/Type/text()))")).isNull();
        assertThat(ExtractionField.xpathProblem("Data[@Name != 'x']/@Value")).isNull();
        assertThat(ExtractionField.xpathProblem("concat(First, ' doc(x) ', Last)")).isNull();
        assertThat(ExtractionField.xpathProblem("stroom:http-call('https://example.com', '', '', '')"))
                .contains("stroom:http-call()");
        assertThat(ExtractionField.xpathProblem("doc('file:///etc/passwd')")).contains("doc()");
        assertThat(ExtractionField.xpathProblem("unparsed-text('x')")).contains("unparsed-text()");
        assertThat(ExtractionField.xpathProblem("Q{http://saxon.sf.net/}system-id()")).isNotNull();
        assertThat(ExtractionField.xpathProblem("$secret")).contains("variables");
        assertThat(ExtractionField.xpathProblem("Id ! string()")).contains("map operators");
        assertThat(ExtractionField.xpathProblem("Id => upper-case()")).contains("arrow");
        assertThat(ExtractionField.xpathProblem("Event/[")).contains("not a valid XPath");
        assertThat(ExtractionField.xpathProblem(" ")).contains("give the XPath");
        assertThat(ExtractionField.xpathProblem("a".repeat(501))).contains("at most 500");
    }

    @Test
    void codeCantBeHiddenInCommentsOrStrings() {
        // Seen in review: a quote in a comment was taken for the start of a string, hiding the call after it.
        assertThat(ExtractionField.xpathProblem("(:':)unparsed-text('file:///etc/passwd')(:':)"))
                .isEqualTo("its XPath may not have comments.");
        assertThat(ExtractionField.xpathProblem("Id (: a note :)")).isEqualTo("its XPath may not have comments.");
        assertThat(ExtractionField.xpathProblem("doc (: x :) ('y')")).isNotNull();
        assertThat(ExtractionField.xpathProblem("Id (: not closed")).contains("isn't closed");
        assertThat(ExtractionField.xpathProblem("Data[@Name = 'not closed]")).contains("isn't closed");
        // A doubled quote is one quote in the string, not its end.
        assertThat(ExtractionField.xpathProblem("Data[@Name = 'it''s doc(x)']/@Value")).isNull();
        assertThat(ExtractionField.xpathProblem("concat('a'', ', doc('x'))")).contains("doc()");
        // Q{...} names, and function references.
        assertThat(ExtractionField.xpathProblem("Q{http://www.w3.org/2005/xpath-functions}string(Id)"))
                .contains("braces");
        assertThat(ExtractionField.xpathProblem("Q{stroom}http-call('x', '', '', '')")).contains("braces");
        assertThat(ExtractionField.xpathProblem("string#1(Id)")).isNotNull();
        assertThat(ExtractionField.xpathProblem("environment-variable('HOME')")).contains("environment-variable()");
        assertThat(ExtractionField.xpathProblem("system-property('java.version')"))
                .contains("system-property()");
    }

    @Test
    void aCallAfterAMinusSignIsSeen() {
        // Seen in review: the text check took the minus for part of a name (as in substring-before), so missed the
        // call; each call in the compiled expression is checked too.
        assertThat(ExtractionField.xpathProblem("-doc('http://example.com/x')")).contains("doc()");
        assertThat(ExtractionField.xpathProblem("1 -doc('x')")).contains("doc()");
        assertThat(ExtractionField.xpathProblem("1-doc('x')")).contains("doc");
        assertThat(ExtractionField.xpathProblem("(-unparsed-text('file:///etc/passwd'))"))
                .contains("unparsed-text");
        assertThat(ExtractionField.xpathProblem("string(-number(Bytes))")).isNull();
        assertThat(ExtractionField.xpathProblem("substring-before(Name, '-')")).isNull();
        assertThat(ExtractionField.xpathProblem("count(Data) - number(@Size)")).isNull();
        assertThat(ExtractionField.xpathProblem("Data[last()]/@Value")).isNull();
    }

    @Test
    void everyAllowedFunctionIsAllowedAsCompiled() {
        // Saxon may compile a call as another (e.g. an optimised one): each allowed, as XPath would be written.
        for (final String xpath : List.of("text()", "node()", "string(A)", "normalize-space(A)", "concat(A, B)",
                "substring(A, 1, 2)", "substring-before(A, 'x')", "substring-after(A, 'x')", "string-length(A)",
                "string-join(A, ',')", "upper-case(A)", "lower-case(A)", "translate(A, 'a', 'b')",
                "contains(A, 'x')", "starts-with(A, 'x')", "ends-with(A, 'x')", "not(A)", "true()", "false()",
                "boolean(A)", "number(A)", "round(A)", "floor(A)", "ceiling(A)", "abs(A)", "sum(A)", "count(A)",
                "min(A)", "max(A)", "avg(A)", "exists(A)", "empty(A)", "A[position() = 1]", "A[last()]", "name(A)",
                "local-name(A)", "data(A)", "distinct-values(A)", "format-number(A, '0.0')")) {
            assertThat(ExtractionField.xpathProblem(xpath)).as(xpath).isNull();
        }
    }

    @Test
    void anXPathsCodeIsReadAsXPathReadsIt() {
        assertThat(ExtractionField.code("a[@b = 'x(:y:)'] (: c (: nested :) d :) e"))
                .isEqualTo("a[@b = ''] (::) e");
        assertThat(ExtractionField.code("\"say \"\"hi\"\"\" or 'it''s'")).isEqualTo("\"\" or ''");
        assertThat(ExtractionField.code("'open")).isNull();
        assertThat(ExtractionField.code("(: open")).isNull();
    }

    @Test
    void fieldsThatCantBeReadSayWhy() {
        assertThatThrownBy(() -> ExtractionField.parse("Organisation")).isInstanceOf(WorkspaceException.class)
                .hasMessageContaining("Give fields as a list");
        assertThatThrownBy(() -> ExtractionField.parse(List.of(Map.of("name", "X", "xpath", "doc('y')"))))
                .hasMessageContaining("'X': its XPath uses doc()");
        assertThatThrownBy(() -> ExtractionField.parse(List.of("X")))
                .hasMessageContaining("Each field is an object");
    }
}
