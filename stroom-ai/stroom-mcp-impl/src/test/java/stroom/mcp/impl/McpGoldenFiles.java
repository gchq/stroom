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

package stroom.mcp.impl;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

/// Compares text with a reference ("golden") test resource of this module, as `stroom-ai-impl`'s `GoldenFiles` does
/// for its own (whose test classes this module can't see): on a mismatch the actual text is written to
/// `build/golden-actual/<resource>` and the test fails saying how to accept the change; run with the system property
/// [#UPDATE_PROPERTY] set to `true`, e.g.
///
/// ```
/// ./gradlew :stroom-ai:stroom-mcp-impl:test -DtestUpdateGoldenFiles=true
/// ```
///
/// the resource (under `src/test/resources`) is overwritten with the actual text, to be reviewed with `git diff`.
/// Line endings are made `\n`, with one at the end.
public final class McpGoldenFiles {

    /// The system property that, set to `true`, makes the reference files be overwritten with the actual text rather
    /// than compared with it.
    public static final String UPDATE_PROPERTY = "testUpdateGoldenFiles";

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(McpGoldenFiles.class);
    private static final String MODULE_DIR = "stroom-ai/stroom-mcp-impl";
    private static final String RESOURCES_DIR = "src/test/resources";
    private static final String ACTUAL_DIR = "build/golden-actual";

    private McpGoldenFiles() {
        // Static only.
    }

    /// @return Whether the tests are run to overwrite the reference files ([#UPDATE_PROPERTY] is `true`).
    public static boolean updating() {
        return Boolean.getBoolean(UPDATE_PROPERTY);
    }

    /// Checks that the text is what a test resource holds, or, if [#UPDATE_PROPERTY] is `true`, makes the resource
    /// (under the module's `src/test/resources`) hold it.
    ///
    /// @param resource The resource's path, as the class path has it, e.g. `/stroom/ai/impl/golden/index/plan.md`.
    /// @param actual   The text produced now.
    public static void assertResourceMatches(final String resource, final String actual) {
        final String normalised = normalise(actual);
        final String relative = resource.startsWith("/")
                ? resource.substring(1)
                : resource;
        final Path moduleDir = moduleDir();
        final Path goldenFile = moduleDir.resolve(RESOURCES_DIR).resolve(relative);
        final Path actualFile = moduleDir.resolve(ACTUAL_DIR).resolve(relative);
        if (updating()) {
            write(goldenFile, normalised);
            delete(actualFile);
            LOGGER.info("Updated reference file {}", goldenFile);
            return;
        }
        final String expected = Files.isRegularFile(goldenFile)
                ? normalise(read(goldenFile))
                : null;
        if (normalised.equals(expected)) {
            delete(actualFile);
            return;
        }
        write(actualFile, normalised);
        assertThat(normalised)
                .withFailMessage(() -> "The output has changed: " + resource + " differs from its reference file "
                                       + goldenFile.toAbsolutePath().normalize() + " (the actual text is in "
                                       + actualFile.toAbsolutePath().normalize() + "). If the change is intended, "
                                       + "accept it by running the test again with -D" + UPDATE_PROPERTY
                                       + "=true (which overwrites the reference file), then review it with git diff.")
                .isEqualTo(expected);
    }

    private static String normalise(final String text) {
        final String unixLines = text.replace("\r\n", "\n").replace('\r', '\n');
        return unixLines.endsWith("\n")
                ? unixLines
                : unixLines + "\n";
    }

    // The module's folder: the working folder when Gradle runs the tests, or under it when an IDE runs them from
    // the repository's root.
    private static Path moduleDir() {
        final Path workingDir = Paths.get("").toAbsolutePath();
        if (Files.isDirectory(workingDir.resolve(RESOURCES_DIR))
            && workingDir.endsWith(Paths.get(MODULE_DIR).getFileName())) {
            return workingDir;
        }
        final Path fromRoot = workingDir.resolve(MODULE_DIR);
        if (Files.isDirectory(fromRoot.resolve(RESOURCES_DIR))) {
            return fromRoot;
        }
        throw new IllegalStateException("Unable to find " + MODULE_DIR + " from " + workingDir);
    }

    private static String read(final Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void write(final Path file, final String text) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, text, StandardCharsets.UTF_8);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void delete(final Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
