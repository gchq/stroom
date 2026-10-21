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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestSafeJq {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void testQueries() throws Exception {
        final JsonNode input = MAPPER.readTree("""
                {"events": [{"host": "fw1", "n": 3}, {"host": "fw2", "n": 5}, {"host": "fw1", "n": 1}]}""");

        assertThat(run(".events[] | select(.host == \"fw1\") | .n", input)).containsExactly("3", "1");
        assertThat(run("[.events[].n] | add", input)).containsExactly("9");
        assertThat(run(".events | group_by(.host) | map({host: .[0].host, count: length})", input))
                .containsExactly("[{\"host\":\"fw1\",\"count\":2},{\"host\":\"fw2\",\"count\":1}]");
        assertThat(run("[limit(3; range(10))]", input)).containsExactly("[0,1,2]");
        assertThat(run(".events[0].host | ascii_upcase", input)).containsExactly("\"FW1\"");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "env",
            "env.HOME",
            "\"\\(env)\"",
            "input",
            "[inputs]",
            "debug",
            "debug(\"x\")",
            "stderr",
            "input_filename",
            "halt",
            "halt_error"})
    void testUnsafeFunctionsAreNotAvailable(final String query) {
        assertThatThrownBy(() -> run(query, MAPPER.getNodeFactory().textNode("aaaa")))
                .isInstanceOf(WorkspaceException.class);
    }

    @Test
    void testEnvironmentVariableIsEmpty() {
        assertThat(run("$ENV", MAPPER.getNodeFactory().nullNode())).containsExactly("{}");
        assertThat(run("$ENV.PATH", MAPPER.getNodeFactory().nullNode())).containsExactly("null");
    }

    @Test
    void testModulesCannotBeLoaded() {
        assertThatThrownBy(() -> run("import \"/etc/passwd\" as p; .", MAPPER.getNodeFactory().nullNode()))
                .isInstanceOf(WorkspaceException.class);
        assertThatThrownBy(() -> run("include \"x\"; .", MAPPER.getNodeFactory().nullNode()))
                .isInstanceOf(WorkspaceException.class);
    }

    @Test
    void testRunawayQueriesAreStopped() {
        final JsonNode input = MAPPER.getNodeFactory().nullNode();

        assertThatThrownBy(() -> run("[range(1e12)] | length", input))
                .hasMessageContaining("too much work");
        assertThatThrownBy(() -> run("reduce range(1e12) as $x (0; . + 1)", input))
                .hasMessageContaining("too much work");
        assertThatThrownBy(() -> run("[0 | recurse(. + 1)]", input))
                .isInstanceOf(WorkspaceException.class);
        assertThatThrownBy(() -> run("def f: f; f", input))
                .isInstanceOf(WorkspaceException.class);
        assertThatThrownBy(() -> run("last(range(1e12))", input))
                .hasMessageContaining("too much work");
    }

    @Test
    void testTimeLimit() {
        assertThatThrownBy(() -> ToolDeadline.run(Duration.ofMillis(1), () -> {
            busyWait();
            return run("[range(1e6)] | length", MAPPER.getNodeFactory().nullNode());
        })).isInstanceOf(ToolTimeoutException.class);
    }

    @Test
    void testBadQueries() {
        assertThatThrownBy(() -> SafeJq.compile("")).isInstanceOf(WorkspaceException.class);
        assertThatThrownBy(() -> SafeJq.compile(".[")).hasMessageContaining("not a valid jq query");
        assertThatThrownBy(() -> SafeJq.compile(".a".repeat(5_001))).hasMessageContaining("at most 10000");
        // A long query, e.g. one that rebuilds a large document, is allowed.
        assertThat(run("[" + "1,".repeat(3_000) + "1] | length", MAPPER.getNodeFactory().nullNode()))
                .containsExactly("3001");
    }

    private static List<String> run(final String query, final JsonNode input) {
        return SafeJq.run(SafeJq.compile(query), input).stream().map(JsonNode::toString).toList();
    }

    private static void busyWait() {
        final long end = System.nanoTime() + Duration.ofMillis(5).toNanos();
        while (System.nanoTime() < end) {
            Thread.onSpinWait();
        }
    }
}
