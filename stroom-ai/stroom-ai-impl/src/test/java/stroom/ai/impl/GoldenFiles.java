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

package stroom.ai.impl;

import stroom.ai.impl.tools.sample.ToolJson;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/// Compares text with a reference ("golden") file checked in under the module's test resources, so that a change to
/// what the model is shown (a tool's description or schema, the prompt, a tool's output), or to what a generator or
/// helper gives, fails a test until someone accepts it. Most are under
/// `stroom-ai/stroom-ai-impl/src/test/resources/stroom/ai/impl/golden/` ([#assertMatches(String, String)]); any
/// other test resource can be one too ([#assertResourceMatches(String, String)]), e.g. a list of recorded cases, each
/// with what it must give, which its test rewrites as a whole with [#writeResource(String, String)].
///
/// As `expected.yaml` and `searchRequestPortrait-current.txt` do elsewhere in the repository: on a mismatch, the
/// actual text is written to `build/golden-actual/<resource>` (git-ignored), so the two can be compared with any diff
/// tool, and the test fails saying how to accept the change. To accept every change, run the tests with the
/// system property [#UPDATE_PROPERTY] set to `true`, e.g.
///
/// ```
/// ./gradlew :stroom-ai:stroom-ai-impl:test --tests '*Reference*' --tests '*Catalogue*' \
///     -DtestUpdateGoldenFiles=true
/// ```
///
/// which overwrites the reference files with the actual text; then review the change with `git diff` before it is
/// committed. (The property starts with `test` as the build only passes such properties on to the test JVMs.) The
/// files are written under `src/test/resources`, not the copies the tests read from the class path, so where one
/// golden file is the input of another's test (the generated XSLTs are read back by the reader's tests), run the
/// update twice.
///
/// Line endings are made `\n`, with one at the end, so the files compare the same on any platform.
public final class GoldenFiles {

    /// The system property that, set to `true`, makes the reference files be overwritten with the actual text rather
    /// than compared with it.
    public static final String UPDATE_PROPERTY = "testUpdateGoldenFiles";

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(GoldenFiles.class);
    private static final String MODULE_DIR = "stroom-ai/stroom-ai-impl";
    private static final String RESOURCES_DIR = "src/test/resources";
    private static final String GOLDEN_RESOURCE = "/stroom/ai/impl/golden/";
    private static final String ACTUAL_DIR = "build/golden-actual";

    private GoldenFiles() {
        // Static only.
    }

    /// @return Whether the tests are run to overwrite the reference files ([#UPDATE_PROPERTY] is `true`).
    public static boolean updating() {
        return Boolean.getBoolean(UPDATE_PROPERTY);
    }

    /// Checks that the text is what the reference file holds, or, if [#UPDATE_PROPERTY] is `true`, makes the
    /// reference file hold it.
    ///
    /// @param name   The reference file's name, relative to the golden folder, e.g. `tools/catalogue.txt`.
    /// @param actual The text produced now.
    public static void assertMatches(final String name, final String actual) {
        assertResourceMatches(GOLDEN_RESOURCE + name, actual);
    }

    /// Checks that a JSON value, written as the golden JSON files are (`json.dumps(value, indent=1)`), is what the
    /// reference file holds, or, if [#UPDATE_PROPERTY] is `true`, makes the reference file hold it.
    ///
    /// @param name   The reference file's name, relative to the golden folder, e.g. `cef/drafted.json`.
    /// @param actual The value produced now.
    public static void assertJsonMatches(final String name, final Object actual) {
        assertMatches(name, ToolJson.dumps(actual, 1));
    }

    /// Checks that the text is what a test resource holds, or, if [#UPDATE_PROPERTY] is `true`, makes the resource
    /// (under the module's `src/test/resources`) hold it.
    ///
    /// @param resource The resource's path, as the class path has it, e.g.
    ///                 `/stroom/ai/impl/golden/generation/000-x.xslt`.
    /// @param actual   The text produced now.
    public static void assertResourceMatches(final String resource, final String actual) {
        final String normalised = normalise(actual);
        final Path goldenFile = sourceFile(resource);
        final Path actualFile = moduleDir().resolve(ACTUAL_DIR).resolve(relative(resource));
        if (updating()) {
            write(goldenFile, normalised);
            delete(actualFile);
            LOGGER.info("Updated reference file {}", goldenFile);
            return;
        }
        if (!Files.isRegularFile(goldenFile)) {
            write(actualFile, normalised);
            fail("There is no reference file " + goldenFile.toAbsolutePath().normalize()
                 + ". The actual text is in " + actualFile.toAbsolutePath().normalize()
                 + ". " + howToAccept());
        }
        final String expected = normalise(read(goldenFile));
        if (expected.equals(normalised)) {
            delete(actualFile);
            return;
        }
        write(actualFile, normalised);
        assertThat(normalised)
                .withFailMessage(() -> "The output has changed: " + resource + " differs from its reference file."
                                       + "\n  Reference: " + goldenFile.toAbsolutePath().normalize()
                                       + "\n  Actual:    " + actualFile.toAbsolutePath().normalize()
                                       + "\nCompare the two (e.g. with diff). " + howToAccept()
                                       + "\nFirst difference: " + firstDifference(expected, normalised))
                .isEqualTo(expected);
    }

    /// Overwrites a test resource (under the module's `src/test/resources`) with the text: what a test does, when
    /// [#updating()], to a resource of recorded cases with each case's result made what it is now.
    ///
    /// @param resource The resource's path, as the class path has it, e.g.
    ///                 `/stroom/ai/impl/tools/generation/helper_cases.json`.
    /// @param text     Its new text.
    public static void writeResource(final String resource, final String text) {
        final Path file = sourceFile(resource);
        write(file, normalise(text));
        LOGGER.info("Updated reference file {}", file);
    }

    /// @param resource A test resource's path, as the class path has it.
    /// @return What a test comparing with a resource of recorded cases says when a case differs: which resource,
    /// and how to accept the change.
    public static String differsFrom(final String resource) {
        return "Differs from what " + resource + " records. " + howToAccept();
    }

    /// @param text Some text.
    /// @return The text with `\n` line endings, ending with a line ending. Nothing else is changed, so that a
    /// change of only spaces is still caught.
    static String normalise(final String text) {
        final String unixLines = text.replace("\r\n", "\n").replace('\r', '\n');
        return unixLines.endsWith("\n")
                ? unixLines
                : unixLines + "\n";
    }

    private static String howToAccept() {
        return "If the change is intended, accept it by running the test again with -D" + UPDATE_PROPERTY
               + "=true (which overwrites the reference file), then review it with git diff.";
    }

    private static String firstDifference(final String expected, final String actual) {
        final String[] expectedLines = expected.split("\n", -1);
        final String[] actualLines = actual.split("\n", -1);
        final int lines = Math.max(expectedLines.length, actualLines.length);
        for (int i = 0; i < lines; i++) {
            final String expectedLine = i < expectedLines.length
                    ? expectedLines[i]
                    : "<end of file>";
            final String actualLine = i < actualLines.length
                    ? actualLines[i]
                    : "<end of file>";
            if (!expectedLine.equals(actualLine)) {
                return "line " + (i + 1) + "\n  reference: " + expectedLine + "\n  actual:    " + actualLine;
            }
        }
        return "none";
    }

    // The resource's path below a resources folder.
    private static String relative(final String resource) {
        return resource.startsWith("/")
                ? resource.substring(1)
                : resource;
    }

    // The resource's own file, in the module's source, not the copy the tests read from the class path.
    private static Path sourceFile(final String resource) {
        return moduleDir().resolve(RESOURCES_DIR).resolve(relative(resource));
    }

    // The module's folder: the working folder when Gradle runs the tests, or under it when an IDE runs them from
    // the repository's root.
    private static Path moduleDir() {
        final Path workingDir = Paths.get("").toAbsolutePath();
        final String golden = RESOURCES_DIR + GOLDEN_RESOURCE;
        if (Files.isDirectory(workingDir.resolve(golden))) {
            return workingDir;
        }
        final Path fromRoot = workingDir.resolve(MODULE_DIR);
        if (Files.isDirectory(fromRoot.resolve(golden))) {
            return fromRoot;
        }
        throw new IllegalStateException("Unable to find " + golden + " from " + workingDir);
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
