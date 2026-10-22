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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// The ported tools' lenient arguments (the Stroom MCP server's `utils/params.py` `ONE_OR_MORE`, and its test in
/// `tests/test_params.py`).
class TestToolArgs {

    @Test
    void oneOrMoreReadsWhatModelsSend() {
        assertThat(ToolArgs.oneOrMore((Object) null)).isEmpty();
        assertThat(ToolArgs.oneOrMore(List.of(1, 2))).containsExactly(1, 2);
        assertThat(ToolArgs.oneOrMore(15783601)).containsExactly(15783601);
        assertThat(ToolArgs.oneOrMore("['Feed']")).containsExactly("Feed");
        assertThat(ToolArgs.oneOrMore("[\"Feed\", \"Pipeline\"]")).containsExactly("Feed", "Pipeline");
        assertThat(ToolArgs.oneOrMore("[1, 2]")).containsExactly(1L, 2L);
        assertThat(ToolArgs.oneOrMore("Feed")).containsExactly("Feed");
        assertThat(ToolArgs.oneOrMore("Feed, Pipeline")).containsExactly("Feed", "Pipeline");
        assertThat(ToolArgs.oneOrMore("  ")).isEmpty();
        assertThat(ToolArgs.oneOrMore("[{\"field\": \"UserId\", \"value\": \"alice\"}]"))
                .containsExactly(Map.of("field", "UserId", "value", "alice"));
        assertThat(ToolArgs.oneOrMore("{\"field\": \"UserId\", \"value\": \"alice\"}"))
                .containsExactly(Map.of("field", "UserId", "value", "alice"));
    }

    @Test
    void aPythonListLiteralIsReadAsAstLiteralEvalReadsIt() {
        assertThat(ToolArgs.oneOrMore("[True, None, 'x']")).containsExactly(true, null, "x");
        assertThat(ToolArgs.oneOrMore("{'field': 'UserId'}")).containsExactly(Map.of("field", "UserId"));
        // Neither JSON nor a Python literal: read as text.
        assertThat(ToolArgs.oneOrMore("[not, a list]")).containsExactly("[not", "a list]");
    }

    @Test
    void aFlagIsTrueOrItsText() {
        assertThat(ToolArgs.flag(true)).isTrue();
        assertThat(ToolArgs.flag(" true ")).isTrue();
        assertThat(ToolArgs.flag("TRUE")).isTrue();
        assertThat(ToolArgs.flag("false")).isFalse();
        assertThat(ToolArgs.flag("yes")).isFalse();
        assertThat(ToolArgs.flag(null)).isFalse();
        assertThat(ToolArgs.flag(1)).isTrue();
        assertThat(ToolArgs.flag(0)).isFalse();
        assertThat(ToolArgs.flag(call("{\"reuse\": true}"), "reuse")).isTrue();
        assertThat(ToolArgs.flag(call("{}"), "reuse")).isFalse();
    }

    @Test
    void rawAndStringsReadTheArgumentsAsGiven() {
        final ToolCall call = call("{\"fields\": [\"a\", 2, null, {\"b\": 1}], \"one\": \"x\"}");
        assertThat(ToolArgs.raw(call, "one")).isEqualTo("x");
        assertThat(ToolArgs.raw(call, "missing")).isNull();
        assertThat(ToolArgs.strings(call, "fields")).containsExactly("a", "2", "{\"b\": 1}");
        assertThat(ToolArgs.strings(call, "one")).containsExactly("x");
    }

    @Test
    void anObjectMayComeAsJsonText() {
        assertThat(ToolArgs.object(call("{\"changes\": {\"a\": 1}}"), "changes")).isEqualTo(Map.of("a", 1));
        assertThat(ToolArgs.object(call("{\"changes\": \"{\\\"a\\\": 1}\"}"), "changes")).isEqualTo(Map.of("a", 1L));
        assertThat(ToolArgs.object(call("{\"changes\": \"[1]\"}"), "changes")).isEqualTo(List.of(1L));
        assertThat(ToolArgs.object(call("{\"changes\": \"  \"}"), "changes")).isNull();
        assertThat(ToolArgs.object(call("{}"), "changes")).isNull();
        assertThatThrownBy(() -> ToolArgs.object(call("{\"changes\": \"{'a': 1}\"}"), "changes"))
                .isInstanceOf(ToolRefusal.class)
                .hasMessage("changes is text that is not JSON (Expecting property name enclosed in double quotes at "
                            + "character 1): pass it as an object");
    }

    @Test
    void aMapIsAnObjectOrItsJsonAndNothingElse() {
        assertThat(ToolArgs.map(call("{\"spec\": {\"kind\": \"csv\"}}"), "spec")).isEqualTo(Map.of("kind", "csv"));
        assertThat(ToolArgs.map(call("{\"spec\": \"{\\\"kind\\\": \\\"csv\\\"}\"}"), "spec"))
                .isEqualTo(Map.of("kind", "csv"));
        assertThat(ToolArgs.map(call("{}"), "spec")).isNull();
        assertThatThrownBy(() -> ToolArgs.map(call("{\"spec\": \"csv\"}"), "spec"))
                .isInstanceOf(ToolRefusal.class)
                .hasMessage("spec is an object, not 'csv'");
        assertThatThrownBy(() -> ToolArgs.map(call("{\"spec\": [1]}"), "spec"))
                .hasMessage("spec is an object, not '[1]'");
        assertThatThrownBy(() -> ToolArgs.map(call("{\"spec\": \"{kind}\"}"), "spec"))
                .hasMessage("spec is text that is not JSON (Expecting property name enclosed in double quotes at "
                            + "character 1): pass it as an object");
    }

    @Test
    void textThatIsNotJsonIsRefusedAsThePythonToolsWordIt() {
        assertThat(ToolArgs.notJson("overrides", "Expecting value", "pass a list of objects").getMessage())
                .isEqualTo("overrides is text that is not JSON (Expecting value): pass a list of objects");
        assertThat(ToolArgs.notJson("header", "Expecting value at character 0", null).getMessage())
                .isEqualTo("header is text that is not JSON (Expecting value at character 0)");
    }

    @Test
    void aPythonLiteralIsReadAsJsonWouldBe() {
        assertThat(ToolArgs.literal("{'V1': 'V2'}")).isEqualTo(Map.of("V1", "V2"));
        assertThat(ToolArgs.literal("[False]")).isEqualTo(List.of(false));
        assertThat(ToolArgs.literal("{\"a\": 'b'}")).isNull();
        assertThat(ToolArgs.literal("V1 to V2")).isNull();
    }

    private static ToolCall call(final String arguments) {
        return new ToolCall("c1", "tool", arguments);
    }
}
