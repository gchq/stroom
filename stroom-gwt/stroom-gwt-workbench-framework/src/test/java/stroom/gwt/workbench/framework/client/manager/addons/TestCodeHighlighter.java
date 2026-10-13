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

package stroom.gwt.workbench.framework.client.manager.addons;

import stroom.gwt.workbench.framework.client.manager.addons.CodeHighlighter.Kind;
import stroom.gwt.workbench.framework.client.manager.addons.CodeHighlighter.Token;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class TestCodeHighlighter {

    @Test
    void testTokenize() {
        final List<Token> tokens = CodeHighlighter.tokenize(
                "userEvent.click(within(<div#workbench-root>).getByRole(\"button\", { name: \"Save\" }))");

        assertThat(tokens)
                .extracting(Token::getKind, Token::getText)
                .containsExactly(
                        tuple(Kind.TEXT, "userEvent."),
                        tuple(Kind.METHOD, "click"),
                        tuple(Kind.TEXT, "("),
                        tuple(Kind.METHOD, "within"),
                        tuple(Kind.TEXT, "("),
                        tuple(Kind.ELEMENT, "<div#workbench-root>"),
                        tuple(Kind.TEXT, ")."),
                        tuple(Kind.METHOD, "getByRole"),
                        tuple(Kind.TEXT, "("),
                        tuple(Kind.STRING, "\"button\""),
                        tuple(Kind.TEXT, ", { name: "),
                        tuple(Kind.STRING, "\"Save\""),
                        tuple(Kind.TEXT, " }))"));
    }

    @Test
    void testTokensMakeUpTheWholeText() {
        final String code = "expect(<span.gwt-InlineLabel>).not.toHaveClass(\"a \\\"quoted\\\" b\")";
        assertThat(CodeHighlighter.tokenize(code).stream().map(Token::getText).collect(Collectors.joining()))
                .isEqualTo(code);
    }

    @Test
    void testUnterminated() {
        assertThat(CodeHighlighter.tokenize("a(\"oops").get(2).getKind()).isEqualTo(Kind.STRING);
        assertThat(CodeHighlighter.tokenize("<div").get(0).getKind()).isEqualTo(Kind.ELEMENT);
    }

    @Test
    void testLessThanIsNotAnElement() {
        assertThat(CodeHighlighter.tokenize("1 < 2"))
                .extracting(Token::getKind)
                .containsOnly(Kind.TEXT);
    }

    @Test
    void testNullAndEmpty() {
        assertThat(CodeHighlighter.tokenize(null)).isEmpty();
        assertThat(CodeHighlighter.tokenize("")).isEmpty();
    }
}
