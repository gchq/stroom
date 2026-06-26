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

package stroom.query.language.token;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public abstract class AbstractQueryTest {

    private static final String PART_DELIMITER = "\n-----\n";
    private static final String IO_DELIMITER = "\n=====\n";

    // If we are editing the input or behaviour then we don't want to test the validity but instead create the expected
    // test output.
    private static final boolean CREATE_MODE = false;

    protected abstract Path getTestDir();

    @TestFactory
    Stream<DynamicTest> buildTests() throws IOException {
        final Path dir = getTestDir();
        final Path expectedFile = dir.resolve("expected.txt");
        final Path inFile = dir.resolve("in.txt");
        final Path outFile = dir.resolve("out.txt");

        Files.deleteIfExists(outFile);
        if (CREATE_MODE) {
            Files.deleteIfExists(expectedFile);
        }

        final String in = Files.readString(inFile);
        final Pattern pattern = Pattern.compile("(?:^|\n)-----[^\n]*\n?");
        final Matcher matcher = pattern.matcher(in);

        final List<String> inputs = new ArrayList<>();
        int end = 0;
        while (matcher.find(end)) {
            final String substring = in.substring(end, matcher.start());
            if (!substring.isEmpty()) {
                inputs.add(substring);
            }
            end = matcher.end();
        }
        final String substring = in.substring(end);
        if (!substring.isEmpty()) {
            inputs.add(substring);
        }


//        final String[] inputs = in.split(PART_DELIMITER);

        final AtomicInteger count = new AtomicInteger();
//        return Arrays
//                .stream(inputs)
//                .filter(input -> !input.isEmpty())
//                .map(input -> {
//                    final int testNum = count.incrementAndGet();
//                    return DynamicTest.dynamicTest(
//                            "Test " + testNum,
//                            () -> testInput(input, outFile, expectedFile, testNum));
//                });

        return inputs
                .stream()
                .map(input -> {
                    final int testNum = count.incrementAndGet();
                    return DynamicTest.dynamicTest(
                            "Test " + testNum,
                            () -> testInput(input, outFile, expectedFile, testNum));
                });
    }

    protected abstract String convert(String input);

    private void testInput(final String input,
                           final Path outFile,
                           final Path expectedFile,
                           final int testNum) {
        System.out.println("Testing input: " + input);
        SoftAssertions.assertSoftly(softAssertions -> {
            try {
                final String tokenString = convert(input).trim();
                appendToFile(outFile, input, tokenString);

                if (CREATE_MODE) {
                    appendToFile(expectedFile, input, tokenString);

                } else {
                    // If we aren't creating tests then actually test.
                    final String allExpected = Files.readString(expectedFile);
                    final String allOut = Files.readString(outFile);

                    final String[] expectedParts = allExpected.split(PART_DELIMITER);
                    final String[] outParts = allOut.split(PART_DELIMITER);

                    softAssertions.assertThat(expectedParts.length).isGreaterThanOrEqualTo(testNum);
                    softAssertions.assertThat(outParts.length).isEqualTo(testNum);

                    final int index = testNum - 1;
                    if (expectedParts.length > index && outParts.length > index) {
                        final String expected = expectedParts[index];
                        final String out = outParts[index];
                        softAssertions.assertThat(out).isEqualTo(expected);
                    }
                }

            } catch (final RuntimeException | IOException e) {
                softAssertions.fail(e.getMessage(), e);
            }
        });
    }

    private void appendToFile(final Path file, final String input, final String output) throws IOException {
        try (final Writer writer = Files.newBufferedWriter(file,
                StandardOpenOption.WRITE,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND)) {
            writer.write(input);
            writer.write(IO_DELIMITER);
            writer.write(output);
            writer.write(PART_DELIMITER);
        }
    }
}
