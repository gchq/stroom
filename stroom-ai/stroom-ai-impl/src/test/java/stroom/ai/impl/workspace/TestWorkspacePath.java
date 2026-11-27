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

import stroom.ai.impl.workspace.WorkspacePath.Area;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestWorkspacePath {

    @Test
    void testValidPaths() {
        assertThat(WorkspacePath.parse("/outputs/a.csv")).isEqualTo(new WorkspacePath(Area.OUTPUTS, List.of("a.csv")));
        // Relative paths are from the root.
        assertThat(WorkspacePath.parse("outputs/a.csv").path()).isEqualTo("/outputs/a.csv");
        assertThat(WorkspacePath.parse("/outputs/turn-1/call-2.txt").path()).isEqualTo("/outputs/turn-1/call-2.txt");
        assertThat(WorkspacePath.parse("/attachments/12.md").area()).isEqualTo(Area.ATTACHMENTS);
        assertThat(WorkspacePath.parse("/outputs/My results v2.csv").path())
                .isEqualTo("/outputs/My results v2.csv");
        assertThat(WorkspacePath.parse("/outputs/").isFile()).isFalse();
        assertThat(WorkspacePath.parse("/")).isEqualTo(WorkspacePath.ROOT);
        assertThat(WorkspacePath.parse(" /outputs/a ").path()).isEqualTo("/outputs/a");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            // Traversal.
            "/outputs/../attachments/1.md",
            "/outputs/..",
            "../outputs/a",
            "/outputs/./a",
            "/outputs/a/../../etc/passwd",
            // Encoded forms are never decoded, so they are refused rather than taken literally.
            "/outputs/%2e%2e/a",
            "/outputs/%2e%2e%2fa",
            "/outputs/..%2f..%2fetc",
            "/outputs/%00",
            // Other separators.
            "\\outputs\\a",
            "/outputs/a\\..\\b",
            // NUL and control characters.
            "/outputs/a\nb",
            "/outputs/a\tb",
            // Hidden, empty and trailing dot or space parts.
            "/outputs/.hidden",
            "/outputs//a",
            "/outputs/a.",
            "/outputs/a /b",
            // Outside the workspace.
            "/etc/passwd",
            "C:/Windows",
            "file:///etc/passwd",
            "~/a",
            "/outputs:x",
            ""})
    void testRejected(final String path) {
        assertThatThrownBy(() -> WorkspacePath.parse(path)).isInstanceOf(WorkspaceException.class);
    }

    @Test
    void testNulAndNonAsciiAreRejected() {
        // Look-alikes of '.', '/' and '\\', a NUL, a right-to-left override and other non-ASCII characters.
        final int[] chars = {0x0000, 0xFF0E, 0x2024, 0xFE52, 0xFF0F, 0x2215, 0x2044, 0xFF3C, 0x202E, 0x00E9};
        for (final int c : chars) {
            final String s = String.valueOf((char) c);
            assertThatThrownBy(() -> WorkspacePath.parse("/outputs/a" + s + "b"))
                    .as("U+%04X", c)
                    .isInstanceOf(WorkspaceException.class);
            assertThatThrownBy(() -> WorkspacePath.parse("/outputs/" + s + s + "/a"))
                    .as("U+%04X", c)
                    .isInstanceOf(WorkspaceException.class);
        }
    }

    @Test
    void testLimits() {
        assertThatThrownBy(() -> WorkspacePath.parse(null)).isInstanceOf(WorkspaceException.class);
        assertThatThrownBy(() -> WorkspacePath.parse("/outputs/" + "a/".repeat(9) + "b"))
                .hasMessageContaining("too deep");
        assertThatThrownBy(() -> WorkspacePath.parse("/outputs/" + "a".repeat(600)))
                .hasMessageContaining("too long");
        assertThatThrownBy(() -> WorkspacePath.parse("/outputs/" + "a".repeat(129)))
                .isInstanceOf(WorkspaceException.class);
        assertThat(WorkspacePath.parse("/outputs/" + "a".repeat(128)).isFile()).isTrue();
    }

    @Test
    void testContains() {
        final WorkspacePath outputs = WorkspacePath.parse("/outputs");
        assertThat(WorkspacePath.ROOT.contains(WorkspacePath.parse("/attachments/1.md"))).isTrue();
        assertThat(outputs.contains(WorkspacePath.parse("/outputs/a/b"))).isTrue();
        assertThat(outputs.contains(WorkspacePath.parse("/attachments/1.md"))).isFalse();
        assertThat(WorkspacePath.parse("/outputs/a").contains(WorkspacePath.parse("/outputs/ab"))).isFalse();
        assertThat(WorkspacePath.parse("/outputs/a").contains(WorkspacePath.parse("/outputs/a/b"))).isTrue();
    }
}
