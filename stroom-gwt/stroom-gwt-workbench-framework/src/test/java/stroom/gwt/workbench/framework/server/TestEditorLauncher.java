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

package stroom.gwt.workbench.framework.server;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestEditorLauncher {

    private static final Path FILE = Path.of("/src/Button.java");

    @Test
    void testIdea() {
        assertThat(launcher(Map.of(EditorLauncher.EDITOR_ENV_VAR, "idea")).buildCommand(FILE, 12))
                .contains(List.of("idea", "--line", "12", "/src/Button.java"));
    }

    @Test
    void testIdeaWithPath() {
        assertThat(launcher(Map.of(EditorLauncher.EDITOR_ENV_VAR, "/opt/idea/bin/idea.sh")).buildCommand(FILE, 3))
                .contains(List.of("/opt/idea/bin/idea.sh", "--line", "3", "/src/Button.java"));
    }

    @Test
    void testVsCodeWithArgs() {
        assertThat(launcher(Map.of(EditorLauncher.EDITOR_ENV_VAR, " code  --reuse-window ")).buildCommand(FILE, 7))
                .contains(List.of("code", "--reuse-window", "-g", "/src/Button.java:7"));
    }

    @Test
    void testOtherEditor() {
        assertThat(launcher(Map.of(EditorLauncher.EDITOR_ENV_VAR, "gedit")).buildCommand(FILE, 7))
                .contains(List.of("gedit", "/src/Button.java"));
    }

    @Test
    void testNoEditor() {
        assertThat(launcher(Map.of()).buildCommand(FILE, 1)).isEmpty();
        assertThat(launcher(Map.of(EditorLauncher.EDITOR_ENV_VAR, " ", "PATH", "/missing"))
                .buildCommand(FILE, 1)).isEmpty();
    }

    @Test
    void testFindsEditorOnPath(@TempDir final Path tempDir) throws IOException {
        final Path code = tempDir.resolve("code");
        Files.writeString(code, "#!/bin/sh\n");
        Files.setPosixFilePermissions(code, PosixFilePermissions.fromString("rwxr-xr-x"));
        assertThat(launcher(Map.of("PATH", tempDir.toString())).buildCommand(FILE, 2))
                .contains(List.of("code", "-g", "/src/Button.java:2"));
    }

    @Test
    void testQuotedPathWithSpaces() {
        assertThat(launcher(Map.of(EditorLauncher.EDITOR_ENV_VAR, "\"/opt/My Editors/code\" --wait"))
                .buildCommand(FILE, 4))
                .contains(List.of("/opt/My Editors/code", "--wait", "-g", "/src/Button.java:4"));
    }

    @Test
    void testSplitCommand() {
        assertThat(EditorLauncher.splitCommand("  a  b\tc ")).containsExactly("a", "b", "c");
        assertThat(EditorLauncher.splitCommand("\"a b\" c")).containsExactly("a b", "c");
        assertThat(EditorLauncher.splitCommand("x\"a b\"y")).containsExactly("xa by");
        assertThat(EditorLauncher.splitCommand("a \"\" b")).containsExactly("a", "", "b");
        // An unclosed quote runs to the end
        assertThat(EditorLauncher.splitCommand("\"a b")).containsExactly("a b");
        assertThat(EditorLauncher.splitCommand("   ")).isEmpty();
    }

    @Test
    void testOnlyQuotesIsNoEditor() {
        assertThat(launcher(Map.of(EditorLauncher.EDITOR_ENV_VAR, "\"\"")).buildCommand(FILE, 1)).isEmpty();
    }

    @Test
    void testFindsEditorOnPathOnWindows(@TempDir final Path tempDir) throws IOException {
        final Path dir1 = Files.createDirectories(tempDir.resolve("dir1"));
        final Path dir2 = Files.createDirectories(tempDir.resolve("dir2"));
        // On Windows there's no execute permission, the extension makes it executable
        Files.writeString(dir2.resolve("code.cmd"), "@echo off\r\n");
        Files.writeString(dir1.resolve("code"), "not executable");
        final Map<String, String> environment = Map.of(
                "PATH", dir1 + ";" + dir2,
                "PATHEXT", ".EXE;.CMD");
        assertThat(new EditorLauncher(environment, true).buildCommand(FILE, 2))
                .contains(List.of("code.cmd", "-g", "/src/Button.java:2"));
    }

    @Test
    void testDefaultPathExtOnWindows(@TempDir final Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("idea.exe"), "");
        assertThat(new EditorLauncher(Map.of("PATH", tempDir.toString()), true).buildCommand(FILE, 2))
                .contains(List.of("idea.exe", "--line", "2", "/src/Button.java"));
    }

    @Test
    void testNotOnPathOnWindows(@TempDir final Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("code.txt"), "");
        assertThat(new EditorLauncher(Map.of("PATH", tempDir.toString()), true).buildCommand(FILE, 2)).isEmpty();
    }

    @Test
    void testOpenWithMissingEditor(@TempDir final Path tempDir) {
        final EditorLauncher launcher = launcher(Map.of(
                EditorLauncher.EDITOR_ENV_VAR, tempDir.resolve("missing editor").toString()));
        assertThatThrownBy(() -> launcher.open(FILE, 1)).isInstanceOf(IOException.class);
    }

    private static EditorLauncher launcher(final Map<String, String> environment) {
        return new EditorLauncher(environment);
    }
}
