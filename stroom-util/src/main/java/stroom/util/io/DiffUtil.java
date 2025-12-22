/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.util.io;

import stroom.util.ConsoleColour;
import stroom.util.logging.LogUtil;

import org.assertj.core.util.diff.DiffUtils;
import org.assertj.core.util.diff.Patch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class DiffUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(DiffUtil.class);

    /**
     * Generates a unified diff of the passed files.
     *
     * @param originalFile      The original file to diff
     * @param revisedFile       The revised file to diff
     * @param colouredOutput    True if you want ascii colour codes in the output
     * @param contextLines      Number of lines of context before and after each difference
     * @return True if any differences exist
     */
    public static boolean unifiedDiff(final Path originalFile,
                                      final Path revisedFile,
                                      final boolean colouredOutput,
                                      final int contextLines) {

        return unifiedDiff(
                originalFile,
                revisedFile,
                colouredOutput,
                contextLines,
                createDiffLinesConsumer(
                        FileUtil.getCanonicalPath(originalFile),
                        FileUtil.getCanonicalPath(revisedFile)));
    }

    /**
     * Generates a unified diff of the passed files.
     *
     * @param originalFile      The original file to diff
     * @param revisedFile       The revised file to diff
     * @param colouredOutput    True if you want ascii colour codes in the output
     * @param contextLines      Number of lines of context before and after each difference
     * @param diffLinesConsumer A consumer of the diff output lines
     * @return True if any differences exist
     */
    public static boolean unifiedDiff(final Path originalFile,
                                      final Path revisedFile,
                                      final boolean colouredOutput,
                                      final int contextLines,
                                      final Consumer<List<String>> diffLinesConsumer) {

        final String originalContent = getFileAsString(originalFile);
        final String revisedContent = getFileAsString(revisedFile);

        return diff(
                originalFile.toAbsolutePath().normalize().toString(),
                revisedFile.toAbsolutePath().normalize().toString(),
                originalContent,
                revisedContent,
                colouredOutput, contextLines, diffLinesConsumer
        );
    }

    /**
     * @param originalContent The original text to diff
     * @param revisedContent  The revised test to diff
     * @param colouredOutput  True if you want ascii colour codes in the output
     * @param contextLines    Number of lines of context before and after each difference
     * @return True if any differences exist
     */
    public static boolean unifiedDiff(final String originalContent,
                                      final String revisedContent,
                                      final boolean colouredOutput,
                                      final int contextLines) {
        return unifiedDiff(
                originalContent,
                revisedContent,
                colouredOutput,
                contextLines,
                createDiffLinesConsumer("Original", "Revised"));
    }

    /**
     * @param originalContent   The original text to diff
     * @param revisedContent    The revised test to diff
     * @param colouredOutput    True if you want ascii colour codes in the output
     * @param contextLines      Number of lines of context before and after each difference
     * @param diffLinesConsumer A consumer of the diff output lines
     * @return True if any differences exist
     */
    public static boolean unifiedDiff(final String originalContent,
                                      final String revisedContent,
                                      final boolean colouredOutput,
                                      final int contextLines,
                                      final Consumer<List<String>> diffLinesConsumer) {

        // We have no files so used fixed words to appear in the diff
        return diff(
                "Original",
                "Revised",
                originalContent,
                revisedContent,
                colouredOutput, contextLines, diffLinesConsumer
        );
    }

    private static String getFileAsString(final Path file) {
        try {
            return Files.readString(file);
        } catch (final IOException e) {
            throw new RuntimeException(LogUtil.message("Error opening file {}: {}",
                    file.toAbsolutePath().normalize(), e.getMessage()), e);
        }
    }

    private static boolean diff(final String file1Path,
                                final String file2Path,
                                final String string1,
                                final String string2,
                                final boolean colouredOutput,
                                final int contextLines,
                                final Consumer<List<String>> diffLinesConsumer) {

        final List<String> lines1 = string1.lines()
                .collect(Collectors.toList());
        final List<String> lines2 = string2.lines()
                .collect(Collectors.toList());

        final Patch<String> patch = DiffUtils.diff(lines1, lines2);

        final List<String> unifiedDiff = DiffUtils.generateUnifiedDiff(
                file1Path,
                file2Path,
                lines1,
                patch,
                contextLines);

        if (!unifiedDiff.isEmpty() && diffLinesConsumer != null) {
            if (colouredOutput) {
                final List<String> colouredLines = unifiedDiff.stream()
                        .map(diffLine -> {

                            final ConsoleColour lineColour;
                            if (diffLine.startsWith("+")) {
                                lineColour = ConsoleColour.GREEN;
                            } else if (diffLine.startsWith("-")) {
                                lineColour = ConsoleColour.RED;
                            } else {
                                lineColour = ConsoleColour.NO_COLOUR;
                            }
                            return ConsoleColour.colourise(diffLine, lineColour);
                        })
                        .collect(Collectors.toList());

                diffLinesConsumer.accept(colouredLines);
            } else {
                diffLinesConsumer.accept(unifiedDiff);
            }
        }
        return !unifiedDiff.isEmpty();
    }

    private static Consumer<List<String>> createDiffLinesConsumer(final String originalPathStr,
                                                                  final String revisedPathStr) {
        return diffLines ->
                LOGGER.info("Comparing {} and {}\n{}",
                        originalPathStr,
                        revisedPathStr,
                        String.join("\n", diffLines));
    }
}
