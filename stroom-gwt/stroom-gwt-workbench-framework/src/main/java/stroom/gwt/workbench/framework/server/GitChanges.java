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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/// Finds the files that have changed in git, for React Storybook's change detection, which tags
/// stories as 'new', 'modified' or 'related'.
public class GitChanges {

    private static final Logger LOGGER = LoggerFactory.getLogger(GitChanges.class);
    private static final long TIMEOUT_SECONDS = 10;

    private final Path directory;

    /// @param directory A directory in the git repository.
    public GitChanges(final Path directory) {
        this.directory = directory;
    }

    /// @return The changed files, or empty lists if git isn't available.
    public Changes find() {
        try {
            final Path root = FilePaths.realPath(Path.of(run("rev-parse", "--show-toplevel").trim()));
            return parse(root, run("status", "--porcelain=v1", "-z", "--untracked-files=all"));
        } catch (final IOException | RuntimeException e) {
            LOGGER.debug("Unable to find the changes in git", e);
            return new Changes(List.of(), List.of());
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Changes(List.of(), List.of());
        }
    }

    /// Parses the output of `git status --porcelain=v1 -z`.
    ///
    /// Each entry is `XY <path>` followed by a NUL, and paths are never quoted. A rename or copy
    /// is followed by a second entry holding the original path.
    ///
    /// @param repoRoot  The root of the repository, which the paths are relative to.
    /// @param porcelain The output.
    /// @return The new (untracked, added or copied) and modified (including renamed) files.
    static Changes parse(final Path repoRoot, final String porcelain) {
        final List<Path> added = new ArrayList<>();
        final List<Path> modified = new ArrayList<>();
        final String[] entries = porcelain.split("\0");
        int i = 0;
        while (i < entries.length) {
            final String entry = entries[i];
            i++;
            if (entry.length() < 4 || entry.charAt(2) != ' ') {
                continue;
            }
            final String status = entry.substring(0, 2);
            final Path file = repoRoot.resolve(entry.substring(3)).normalize();
            if (status.indexOf('R') >= 0 || status.indexOf('C') >= 0) {
                // Skip the original path
                i++;
            }
            if ("??".equals(status) || status.indexOf('A') >= 0 || status.indexOf('C') >= 0) {
                added.add(file);
            } else if (status.indexOf('M') >= 0 || status.indexOf('R') >= 0) {
                modified.add(file);
            }
        }
        return new Changes(added, modified);
    }

    private String run(final String... args) throws IOException, InterruptedException {
        final List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(List.of(args));
        // Write the output to a file rather than reading it here, so the wait below can time out
        final Path output = Files.createTempFile("workbench-git", ".out");
        try {
            final Process process = new ProcessBuilder(command)
                    .directory(directory.toFile())
                    .redirectOutput(output.toFile())
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            if (!waitFor(process)) {
                throw new IOException("git " + String.join(" ", args) + " timed out");
            }
            if (process.exitValue() != 0) {
                throw new IOException("git " + String.join(" ", args) + " failed");
            }
            return Files.readString(output, StandardCharsets.UTF_8);
        } finally {
            Files.deleteIfExists(output);
        }
    }

    private static boolean waitFor(final Process process) throws InterruptedException {
        boolean finished = false;
        try {
            finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } finally {
            if (!finished) {
                process.destroyForcibly();
            }
        }
        return finished;
    }


    // --------------------------------------------------------------------------------


    /// The files that have changed.
    public static final class Changes {

        private final List<Path> added;
        private final List<Path> modified;

        /// @param added    Files that are new, i.e. untracked, added or copied.
        /// @param modified Files that have been modified or renamed.
        Changes(final List<Path> added, final List<Path> modified) {
            this.added = added;
            this.modified = modified;
        }

        /// @return Files that are new, i.e. untracked or added.
        public List<Path> getAdded() {
            return added;
        }

        /// @return Files that have been modified.
        public List<Path> getModified() {
            return modified;
        }
    }
}
