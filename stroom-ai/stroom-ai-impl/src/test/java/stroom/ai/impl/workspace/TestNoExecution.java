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

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/// The agent's own tools must never run anything as an operating-system process, run scripts, or reach the
/// network: they only work on the chat's workspace in Java. This fails the build if any of their source uses
/// something that could.
class TestNoExecution {

    private static final List<String> PACKAGES = List.of(
            "src/main/java/stroom/ai/impl/tools",
            "src/main/java/stroom/ai/impl/workspace",
            "src/main/java/stroom/ai/impl/memory");

    private static final List<String> FORBIDDEN = List.of(
            "ProcessBuilder",
            "Runtime.getRuntime",
            "ProcessHandle",
            "javax.script",
            "ScriptEngine",
            "java.net.",
            "HttpClient",
            "URLConnection",
            "java.lang.reflect",
            "Class.forName",
            "System.load",
            "createSymbolicLink",
            "jdk.jshell");

    @Test
    void testToolsDoNotExecuteAnything() throws IOException {
        final List<String> problems = new ArrayList<>();
        int files = 0;
        for (final String dir : PACKAGES) {
            final Path path = Path.of(dir);
            assertThat(path).isDirectory();
            try (final Stream<Path> sources = Files.walk(path)) {
                for (final Path source : sources.filter(p -> p.toString().endsWith(".java")).toList()) {
                    files++;
                    final String text = Files.readString(source);
                    for (final String forbidden : FORBIDDEN) {
                        if (text.contains(forbidden)) {
                            problems.add(source + " uses " + forbidden);
                        }
                    }
                }
            }
        }
        assertThat(files).isPositive();
        assertThat(problems).isEmpty();
    }
}
