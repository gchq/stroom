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

package stroom.ai.impl.workspace;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestSafeJqRegex {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void testTest() {
        assertThat(run("test(\"fw[0-9]+\")", "\"host fw12\"")).containsExactly("true");
        assertThat(run("test(\"FW\")", "\"host fw12\"")).containsExactly("false");
        assertThat(run("test(\"FW\"; \"i\")", "\"host fw12\"")).containsExactly("true");
        assertThat(run("test([\"FW\", \"i\"])", "\"host fw12\"")).containsExactly("true");
        assertThat(run("[.[] | select(test(\"^ERR\"))]", "[\"ERROR a\", \"INFO b\", \"ERR c\"]"))
                .containsExactly("[\"ERROR a\",\"ERR c\"]");
    }

    @Test
    void testMatch() {
        assertThat(run("match(\"(?<n>[0-9]+)\")", "\"ab 42 cd\""))
                .containsExactly("{\"offset\":3,\"length\":2,\"string\":\"42\",\"captures\":[{\"offset\":3,"
                                 + "\"length\":2,\"string\":\"42\",\"name\":\"n\"}]}");
        assertThat(run("[match(\"[0-9]\"; \"g\") | .offset]", "\"a1b2c3\"")).containsExactly("[1,3,5]");
        // A group that takes no part in the match.
        assertThat(run("match(\"a(x)?b\").captures", "\"ab\""))
                .containsExactly("[{\"offset\":-1,\"length\":0,\"string\":null,\"name\":null}]");
        assertThat(run("[match(\"z\")]", "\"abc\"")).containsExactly("[]");
    }

    @Test
    void testOffsetsAreInCodePoints() {
        // As jq gives them, so they can be used to slice the string.
        assertThat(run("match(\"b\") | .offset", "\"😀ab\"")).containsExactly("2");
        assertThat(run("match(\"😀\") | .length", "\"a😀b\"")).containsExactly("1");
    }

    @Test
    void testCaptureAndScan() {
        assertThat(run("capture(\"(?<host>fw[0-9]+) (?<action>[a-z]+)\")", "\"fw1 deny\""))
                .containsExactly("{\"host\":\"fw1\",\"action\":\"deny\"}");
        assertThat(run("[scan(\"[0-9]+\")]", "\"1 22 333\"")).containsExactly("[\"1\",\"22\",\"333\"]");
        assertThat(run("[scan(\"([a-z])([0-9])\")]", "\"a1 b2\"")).containsExactly("[[\"a\",\"1\"],[\"b\",\"2\"]]");
    }

    @Test
    void testSplit() {
        assertThat(run("split(\", *\"; null)", "\"a, b,c\"")).containsExactly("[\"a\",\"b\",\"c\"]");
        assertThat(run("[splits(\"[;|]\")]", "\"a;b|c\"")).containsExactly("[\"a\",\"b\",\"c\"]");
        // split/1 is jq's literal split, which was always available.
        assertThat(run("split(\".\")", "\"a.b\"")).containsExactly("[\"a\",\"b\"]");
    }

    @Test
    void testSubAndGsub() {
        assertThat(run("sub(\"[0-9]+\"; \"N\")", "\"a1 b22\"")).containsExactly("\"aN b22\"");
        assertThat(run("gsub(\"[0-9]+\"; \"N\")", "\"a1 b22\"")).containsExactly("\"aN bN\"");
        assertThat(run("gsub(\"(?<d>[0-9])\"; \"<\\(.d)>\")", "\"a1b2\"")).containsExactly("\"a<1>b<2>\"");
        assertThat(run("gsub(\"A\"; \"x\"; \"i\")", "\"aAa\"")).containsExactly("\"xxx\"");
        // An empty pattern matches between every character, and doesn't loop for ever.
        assertThat(run("gsub(\"\"; \"-\")", "\"ab\"")).containsExactly("\"-a-b-\"");
        // A replacement that gives several values gives a result for each.
        assertThat(run("[sub(\"a\"; \"x\", \"y\")]", "\"ab\"")).containsExactly("[\"xb\",\"yb\"]");
    }

    @Test
    void testFlags() {
        assertThat(run("test(\"a b\"; \"x\")", "\"ab\"")).containsExactly("true");
        assertThat(run("test(\"a.b\")", "\"a\\nb\"")).containsExactly("false");
        // Oniguruma's 'multiline' (m) lets . match a line end; p is s and m.
        assertThat(run("test(\"a.b\"; \"m\")", "\"a\\nb\"")).containsExactly("true");
        assertThat(run("test(\"a.b\"; \"p\")", "\"a\\nb\"")).containsExactly("true");
        assertThat(run("[match(\"x*\"; \"gn\") | .string]", "\"axxb\"")).containsExactly("[\"xx\"]");
        assertThatThrownBy(() -> run("test(\"a\"; \"l\")", "\"a\"")).hasMessageContaining("not available");
        assertThatThrownBy(() -> run("test(\"a\"; \"q\")", "\"a\"")).hasMessageContaining("not valid");
    }

    @Test
    void testInvalidUse() {
        assertThatThrownBy(() -> run("test(\"(\")", "\"a\"")).hasMessageContaining("not a valid regular expression");
        assertThatThrownBy(() -> run("test(\"a\")", "42")).hasMessageContaining("only be applied to strings");
        assertThatThrownBy(() -> run("test(\"" + "a".repeat(SafePattern.MAX_PATTERN_LENGTH + 1) + "\")", "\"a\""))
                .hasMessageContaining("at most " + SafePattern.MAX_PATTERN_LENGTH);
        assertThatThrownBy(() -> run("sub(\"a\"; 1)", "\"a\"")).hasMessageContaining("must give a string");
    }

    @Test
    void testCatastrophicBacktrackingIsStopped() {
        // A pattern that backtracks catastrophically even with the JDK's memoisation of simple loops.
        final String input = MAPPER.getNodeFactory().textNode("a,".repeat(40)).toString();

        assertThatThrownBy(() -> ToolDeadline.run(Duration.ofMillis(200), () ->
                run("test(\"(.*?,){25}x\")", input)))
                .isInstanceOf(ToolTimeoutException.class);
    }

    private static List<String> run(final String query, final String inputJson) {
        try {
            final JsonNode input = MAPPER.readTree(inputJson);
            return SafeJq.run(SafeJq.compile(query), input).stream().map(JsonNode::toString).toList();
        } catch (final com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalArgumentException(e);
        }
    }
}
