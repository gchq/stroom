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

import stroom.ai.impl.workspace.WorkspacePath;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestToolResult {

    @Test
    void aTextResultIsOnlyItsText() {
        final ToolResult result = ToolResult.text("Wrote 3 lines.");

        assertThat(result.text()).isEqualTo("Wrote 3 lines.");
        assertThat(result.isError()).isFalse();
        assertThat(result.structuredContent()).isEmpty();
        assertThat(result.resources()).isEmpty();
        assertThat(result.userActions()).isEmpty();
    }

    @Test
    void aTextResultIsAnErrorWhenItsTextSaysSo() {
        assertThat(ToolResult.text("Error: no such file").isError()).isTrue();
        // Only the prefix the tools use for a failed call counts.
        assertThat(ToolResult.text("Errors found: 2").isError()).isFalse();
        assertThat(ToolResult.text("The Error stream").isError()).isFalse();
        assertThat(ToolResult.error("Error running built-in tool 't': kaboom").isError()).isTrue();
    }

    @Test
    void nullTextIsEmpty() {
        assertThat(ToolResult.text(null).text()).isEmpty();
        assertThat(ToolResult.text(null).isError()).isFalse();
        assertThat(ToolResult.error(null).text()).isEmpty();
    }

    @Test
    void partsAreAddedInOrderWithoutChangingTheOriginal() {
        final ToolResult original = ToolResult.text("done");
        final ToolResource first = ToolResource.workspaceFile(WorkspacePath.parse("/outputs/a.csv"), "A");
        final ToolResource second = ToolResource.workspaceFile("/outputs/b.json", null);
        final ToolUserAction action = new ToolUserAction("create-query-1", "create_query", Map.of("name", "n"));

        final ToolResult result = original.withResource(first)
                .withResources(List.of(second))
                .withResource(null)
                .withResources(null)
                .withUserAction(action)
                .withStructuredContent(Map.of("count", 3));

        assertThat(result.text()).isEqualTo("done");
        assertThat(result.resources()).containsExactly(first, second);
        assertThat(result.userActions()).containsExactly(action);
        assertThat(result.structuredContent()).containsExactly(Map.entry("count", 3));
        assertThat(original.resources()).isEmpty();
        assertThat(original.userActions()).isEmpty();
        assertThat(result.withStructuredContent(null).structuredContent()).isEmpty();
        assertThat(result).isEqualTo(original.withResource(first).withResource(second).withUserAction(action)
                .withStructuredContent(Map.of("count", 3)));
    }

    @Test
    void partsCannotBeChangedFromOutside() {
        final ToolResult result = ToolResult.text("x")
                .withResource(ToolResource.workspaceFile("/outputs/a.txt", null));

        assertThatThrownBy(() -> result.resources().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> result.userActions().add(null)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> result.withUserAction(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void aUserActionGivesItsIdAndKindFirstThenItsArgumentsInOrder() {
        final Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("name", "Windows users");
        arguments.put("query", "from X select Y");
        arguments.put("description", "Who logged on");

        final ToolUserAction action = new ToolUserAction("create-query-7", "create_query", arguments);
        arguments.put("later", "ignored");

        assertThat(action.toMap()).containsExactly(
                Map.entry("id", "create-query-7"),
                Map.entry("action", "create_query"),
                Map.entry("name", "Windows users"),
                Map.entry("query", "from X select Y"),
                Map.entry("description", "Who logged on"));
        assertThat(new ToolUserAction("a", "b", null).toMap()).containsExactly(Map.entry("id", "a"),
                Map.entry("action", "b"));
        assertThatThrownBy(() -> new ToolUserAction(null, "b", Map.of())).isInstanceOf(NullPointerException.class);
    }

    @Test
    void aWorkspaceFilesMediaTypeComesFromACommonExtension() {
        assertThat(ToolResource.workspaceFile("/outputs/a.csv", "d"))
                .isEqualTo(new ToolResource("/outputs/a.csv", "text/csv", "d"));
        assertThat(ToolResource.workspaceFile("/outputs/A.JSON", null).mediaType()).isEqualTo("application/json");
        assertThat(ToolResource.workspaceFile("/documents/x.md", null).mediaType()).isEqualTo("text/markdown");
        assertThat(ToolResource.workspaceFile("/outputs/e.log", null).mediaType()).isEqualTo("text/plain");
        assertThat(ToolResource.workspaceFile("/outputs/e.xml", null).mediaType()).isEqualTo("application/xml");
        assertThat(ToolResource.workspaceFile("/outputs/data.bin", null).mediaType()).isNull();
        assertThat(ToolResource.workspaceFile("/outputs/noext", null).mediaType()).isNull();
        assertThat(ToolResource.workspaceFile("/outputs/trailing.", null).mediaType()).isNull();
        assertThat(ToolResource.workspaceFile("/outputs.d/file", null).mediaType()).isNull();
        assertThatThrownBy(() -> new ToolResource(null, null, null)).isInstanceOf(NullPointerException.class);
    }
}
