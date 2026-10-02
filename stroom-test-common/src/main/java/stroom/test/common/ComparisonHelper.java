/*
 * Copyright 2016 Crown Copyright
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

package stroom.test.common;


import stroom.util.ConsoleColour;
import stroom.util.io.FileUtil;
import stroom.util.io.StreamUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import org.assertj.core.util.diff.DiffUtils;
import org.assertj.core.util.diff.Patch;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Provides methods that are used by multiple tests.
 */

public final class ComparisonHelper {

    public static final String OUTPUT_EXTENSION = ".out";
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ComparisonHelper.class);

    private ComparisonHelper() {
        // Hidden constructor as utility class.
    }

    public static void compareStrings(final String expected, final String actual, final String message) {
        final int index = compareStrings(actual, expected);

        // examine the location if not -1.
        if (index != -1) {
            final String expectedComp = getComparisonString(expected, index);
            final String actualComp = getComparisonString(actual, index);

            if (!expectedComp.equals(actualComp)) {
                System.out.println("Actual XML was");
                System.out.println(actual);
                System.out.println("Expected XML was");
                System.out.println(expected);

                fail(message + index + " wrote output to system out");
            }
        }
    }

    private static int compareStrings(final String str1, final String str2) {
        final char[] arr1 = str1.toCharArray();
        final char[] arr2 = str2.toCharArray();

        for (int i = 0; i < arr1.length; i++) {
            // If arr2 finishes here then the strings are unequal at this index.
            if (arr2.length == i) {
                return i;
            }

            final char c1 = arr1[i];
            final char c2 = arr2[i];

            // If the characters at this index are not equal then this is where
            // the mismatch occurs.
            if (c1 != c2) {
                return i;
            }
        }

        // If arr2 goes on to be longer than arr1 then return a mismatch that is
        // equal to the length of arr1.
        if (arr1.length < arr2.length) {
            return arr1.length;
        }

        return -1;
    }

    /**
     * Generate a unified diff of the two files using the default number of lines of context.
     * The diff output is sent to system out in coloured form.
     *
     * @return True if the files are the same
     */
    public static boolean unifiedDiff(final Path expectedFile, final Path actualFile) {
        return unifiedDiff(expectedFile, actualFile, 3);
    }

    /**
     * Generate a unified diff of the two files using the specified number of lines of context.
     * The diff output is sent to system out in coloured form.
     *
     * @return True if the files are the same
     */
    public static boolean unifiedDiff(final Path expectedFile, final Path actualFile, final int contextLines) {

        boolean areFilesTheSame = true;
        try (final Stream<String> expectedStream = Files.lines(expectedFile);
                final Stream<String> actualStream = Files.lines(actualFile)) {

            final List<String> expectedLines = expectedStream.toList();
            final List<String> actualLines = actualStream.collect(Collectors.toList());

            final Patch<String> patch = DiffUtils.diff(expectedLines, actualLines);

            final List<String> unifiedDiff = DiffUtils.generateUnifiedDiff(
                    expectedFile.toString(),
                    actualFile.toString(),
                    expectedLines,
                    patch,
                    contextLines);

            if (!unifiedDiff.isEmpty()) {
                areFilesTheSame = false;

                LOGGER.error("Differences exist between:\nexpected - {}\nand\nactual - {}",
                        FileUtil.getCanonicalPath(expectedFile),
                        FileUtil.getCanonicalPath(actualFile));

                System.out.println();
                unifiedDiff.forEach(diffLine -> {

                    final ConsoleColour lineColour;
                    if (diffLine.startsWith("+")) {
                        lineColour = ConsoleColour.GREEN;
                    } else if (diffLine.startsWith("-")) {
                        lineColour = ConsoleColour.RED;
                    } else {
                        lineColour = ConsoleColour.NO_COLOUR;
                    }

                    System.out.println(ConsoleColour.colourise(diffLine, lineColour));
                });
                System.out.println(LogUtil.message("\nvimdiff {} {}",
                        FileUtil.getCanonicalPath(expectedFile), FileUtil.getCanonicalPath(actualFile)));

                System.out.println(LogUtil.message(
                        "\nIf you are fully satisfied the differences are valid then you can do\ncp {} {}",
                        FileUtil.getCanonicalPath(actualFile),
                        FileUtil.getCanonicalPath(expectedFile)));
            }

        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
        return areFilesTheSame;
    }

    private static String getComparisonString(final String str, final int index) {
        int start = index - 20;
        int end = index + 20;

        if (start < 0) {
            start = 0;
        }

        if (str.length() < end + 1) {
            end = str.length() - 1;
        }

        return str.substring(start, end);
    }

    public static void compareDirs(final Path in, final Path out) {
        final List<Path> inFiles = list(in);
        final List<Path> outFiles = list(out);

        // Tell the tester what went wrong
        if (outFiles.size() != inFiles.size()) {
            final List<Path> inFilenames = inFiles.stream().map(Path::getFileName).toList();
            final List<Path> outFilenames = outFiles.stream().map(Path::getFileName).toList();

            final List<Path> extraFiles = new ArrayList<>(outFilenames);
            extraFiles.removeAll(inFilenames);
            final List<Path> missingFiles = new ArrayList<>(inFilenames);
            missingFiles.removeAll(outFilenames);

            if (!extraFiles.isEmpty()) {
                LOGGER.error("New files added:\n{}",
                        extraFiles
                                .stream()
                                .map(path -> "    " + path.toString())
                                .collect(Collectors.joining("\n")));
            }
            if (!missingFiles.isEmpty()) {
                LOGGER.error("Missing files:\n{}",
                        missingFiles
                                .stream()
                                .map(path -> "    " + path.toString())
                                .collect(Collectors.joining("\n")));
            }
        }

        // Do the test
        assertThat(outFiles.size()).as(
                "Dir " + FileUtil.getCanonicalPath(in) + " does not contain the same number of files as "
                + FileUtil.getCanonicalPath(out)).isEqualTo(inFiles.size());

        for (final Path inFile : inFiles) {
            Path outFile = null;

            // Get corresponding output file.
            for (final Path tmp : outFiles) {
                if (tmp.getFileName().toString().equals(inFile.getFileName().toString())) {
                    outFile = tmp;
                    break;
                }
            }

            // Make sure we found the file.
            assertThat(outFile)
                    .as("Output file not found for: " + FileUtil.getCanonicalPath(inFile)
                        + " in directory \"" + FileUtil.getCanonicalPath(out) + "\"")
                    .isNotNull();

            LOGGER.debug("Comparing \"{}\" and \"{}\"",
                    FileUtil.getCanonicalPath(inFile),
                    FileUtil.getCanonicalPath(outFile));

            if (Files.isDirectory(inFile)) {
                // Make sure files are the same type.
                assertThat(Files.isDirectory(outFile))
                        .as("Output file is not a directory for: " + FileUtil.getCanonicalPath(inFile))
                        .isTrue();

                // Recurse.
                compareDirs(inFile, outFile);

            } else if (Files.isRegularFile(inFile)) {
                compareFiles(inFile, outFile);
            }
        }
    }

    private static List<Path> list(final Path path) {
        final List<Path> list = new ArrayList<>();
        try (final DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
            stream.forEach(file -> {
                try {
                    if (!Files.isHidden(file)) {
                        list.add(file);
                    }
                } catch (final IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
        return list;
    }

    public static void compareFiles(final Path expectedFile, final Path actualFile) {
        compareFiles(expectedFile, actualFile, false, true);
    }

    public static void compareFiles(final Path expectedFile, final Path actualFile, final boolean ignoreWhitespace,
                                    final boolean xml) {
        try {
            compare(expectedFile, actualFile, ignoreWhitespace, xml);
        } catch (final RuntimeException e) {
            fail(e.getMessage());
        }
    }

    public static void compare(final Path expectedFile, final Path actualFile, final boolean ignoreWhitespace,
                               final boolean xml) {
        // Make sure both files exist.
        if (!Files.isRegularFile(expectedFile)) {
            throw new RuntimeException(
                    "Expected file '" + FileUtil.getCanonicalPath(expectedFile) + "' does not exist");
        }
        if (!Files.isRegularFile(actualFile)) {
            throw new RuntimeException("Actual file '" + FileUtil.getCanonicalPath(actualFile) + "' does not exist");
        }

        Reader reader1 = null;
        Reader reader2 = null;
        try {
            reader1 = new BufferedReader(new InputStreamReader(Files.newInputStream(expectedFile),
                    StreamUtil.DEFAULT_CHARSET));
            reader2 = new BufferedReader(new InputStreamReader(Files.newInputStream(actualFile),
                    StreamUtil.DEFAULT_CHARSET));

            if (!doCompareReaders(reader1, reader2, ignoreWhitespace, xml)) {
                throw new RuntimeException(LogUtil.message(
                        "Files are not the same: \n" +
                        "{}\n" +
                        "{}\n\n" +
                        "vimdiff {} {}\n" +
                        "If you are satisfied the differences are valid then you can do\n" + "cp {} {}",
                        FileUtil.getCanonicalPath(expectedFile),
                        FileUtil.getCanonicalPath(actualFile),
                        FileUtil.getCanonicalPath(expectedFile),
                        FileUtil.getCanonicalPath(actualFile),
                        FileUtil.getCanonicalPath(actualFile),
                        FileUtil.getCanonicalPath(expectedFile)
                ));
            }

        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            if (reader1 != null) {
                try {
                    reader1.close();
                } catch (final IOException e) {
                    // Swallow as trying to ensure it is closed.
                }
            }
            if (reader2 != null) {
                try {
                    reader2.close();
                } catch (final IOException e) {
                    // Swallow as trying to ensure it is closed.
                }
            }
        }
    }

    public static void compareStreams(final InputStream is1, final InputStream is2, final boolean ignoreWhitespace,
                                      final boolean xml) {
        if (!doCompareReaders(new InputStreamReader(is1), new InputStreamReader(is2), ignoreWhitespace, xml)) {
            fail("Content is not the same");
        }
    }

    public static void compareReaders(final Reader reader1, final Reader reader2, final boolean ignoreWhitespace,
                                      final boolean xml) {
        if (!doCompareReaders(reader1, reader2, ignoreWhitespace, xml)) {
            fail("Content is not the same");
        }
    }

    private static boolean doCompareReaders(final Reader reader1, final Reader reader2, final boolean ignoreWhitespace,
                                            final boolean xml) {
        try {
            // Comparing XML is more expensive so disable it for now as tests
            // pass without comparing as XML.
            if (!compare(reader1, reader2, ignoreWhitespace)) {
                return false;
            }
        } catch (final IOException e) {
            fail(e.getMessage());
        } finally {
            if (reader1 != null) {
                try {
                    reader1.close();
                } catch (final IOException e) {
                    // Swallow as trying to ensure it is closed.
                }
            }
            if (reader2 != null) {
                try {
                    reader2.close();
                } catch (final IOException e) {
                    // Swallow as trying to ensure it is closed.
                }
            }
        }

        return true;
    }

    private static boolean compare(final Reader reader1, final Reader reader2, final boolean ignoreWhitespace)
            throws IOException {
        int c1 = 0;
        int c2 = 0;
        while (c1 != -1 && c2 != -1) {
            c1 = nextChar(reader1, ignoreWhitespace);
            c2 = nextChar(reader2, ignoreWhitespace);
            if (c1 != c2) {
                return false;
            }
        }

        return true;
    }

    private static int nextChar(final Reader reader, final boolean ignoreWhitespace) throws IOException {
        int i = -1;
        while (true) {
            i = reader.read();
            if (i == -1) {
                return i;
            } else if (ignoreWhitespace) {
                if (!Character.isWhitespace((char) i)) {
                    return i;
                }
            } else {
                return i;
            }
        }
    }

    private static boolean compareXML(final Reader reader1, final Reader reader2, final boolean ignoreWhitespace)
            throws IOException {
        String fragment1 = null;
        String fragment2 = null;
        do {
            fragment1 = nextXMLFragment(reader1, ignoreWhitespace);
            fragment2 = nextXMLFragment(reader2, ignoreWhitespace);

            if (!fragment1.equals(fragment2)) {
                return false;
            }

        } while (fragment1.length() > 0 && fragment2.length() > 0);

        return true;
    }

    private static final String nextXMLFragment(final Reader reader, final boolean ignoreWhitespace)
            throws IOException {
        final StringBuilder builder = new StringBuilder();
        boolean inElement = false;
        boolean inAttribute = false;
        boolean inQuotes = false;

        final List<String> attributes = new ArrayList<>();
        final StringBuilder attribute = new StringBuilder();

        while (true) {
            final int i = nextChar(reader, ignoreWhitespace);
            if (i == -1) {
                return builder.toString();
            }

            final char c = (char) i;
            if (!inQuotes && c == '<') {
                inElement = true;
                inAttribute = false;
            }
            if (inAttribute && c == '\"') {
                inQuotes = !inQuotes;
            }
            if (!inQuotes && (c == '>' || c == '/')) {
                inElement = false;
                inAttribute = false;
                inQuotes = false;

                if (attribute.length() > 0) {
                    attributes.add(attribute.toString());
                    attribute.setLength(0);
                }
                Collections.sort(attributes);
                for (final String att : attributes) {
                    builder.append(" ");
                    builder.append(att);
                }
                attributes.clear();
                builder.append(c);
                return builder.toString();
            }
            if (inElement) {
                if (isCharNothing(c)) {
                    inAttribute = true;
                }
                if (!inAttribute) {
                    builder.append(c);
                } else {
                    if (isCharNothing(c)) {
                        if (attribute.length() > 0) {
                            attributes.add(attribute.toString());
                            attribute.setLength(0);
                        }
                    } else {
                        attribute.append(c);
                    }
                }
            } else {
                builder.append(c);
            }
        }
    }

    private static boolean isCharNothing(final char c) {
        return c == ' ' || c == '\n';
    }
}
