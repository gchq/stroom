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

import org.junit.jupiter.api.Assumptions;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/// Runs git in a temporary repository for tests, skipping the test if git isn't available.
final class GitTestUtil {

    private GitTestUtil() {
        // Static utility
    }

    /// Creates a repository, skipping the test if git isn't available.
    ///
    /// @param directory The directory to make a repository.
    static void init(final Path directory) {
        Assumptions.assumeTrue(tryRun(directory, "--version"), "git isn't available");
        git(directory, "init", "-q");
    }

    /// Stages and commits everything in the repository.
    ///
    /// @param directory The repository.
    static void commitAll(final Path directory) {
        git(directory, "add", "-A");
        git(directory, "-c", "user.name=Test", "-c", "user.email=test@example.com", "-c", "commit.gpgsign=false",
                "commit", "-q", "-m", "Test");
    }

    /// Runs git, failing the test if it fails.
    ///
    /// @param directory The repository.
    /// @param args      The git arguments.
    static void git(final Path directory, final String... args) {
        if (!tryRun(directory, args)) {
            throw new IllegalStateException("git " + String.join(" ", args) + " failed");
        }
    }

    private static boolean tryRun(final Path directory, final String... args) {
        final List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(List.of(args));
        try {
            final Process process = new ProcessBuilder(command)
                    .directory(directory.toFile())
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            if (!process.waitFor(30, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (final IOException e) {
            return false;
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
