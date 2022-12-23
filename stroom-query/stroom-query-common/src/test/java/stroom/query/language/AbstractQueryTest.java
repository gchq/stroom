package stroom.query.language;

import stroom.query.common.v2.TestFileUtil;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public abstract class AbstractQueryTest {

    // If we are editing the input or behaviour then we don't want to test the validity but instead create the expected
    // test output.
    private static final boolean CREATE_MODE = true;

    abstract String getTestDirName();

    @TestFactory
    Stream<DynamicTest> buildTests() throws IOException {
        final Path dir = TestFileUtil.getTestResourcesDir().resolve(getTestDirName());
        final Path expectedFile = dir.resolve("expected.txt");
        final Path inFile = dir.resolve("in.txt");
        final Path outFile = dir.resolve("out.txt");

        Files.deleteIfExists(outFile);
        if (CREATE_MODE) {
            Files.deleteIfExists(expectedFile);
        }

        final String in = Files.readString(inFile);
        final String[] inputs = in.split("\n-----\n");

        final AtomicInteger count = new AtomicInteger();
        return Arrays
                .stream(inputs)
                .filter(input -> input.length() > 0)
                .map(input -> {
                    final int testNum = count.incrementAndGet();
                    return DynamicTest.dynamicTest(
                            "Test " + testNum,
                            () -> testInput(input, outFile, expectedFile, testNum));
                });
    }

    abstract String convert(String input);

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

                    final String[] expectedParts = allExpected.split("\n\n");
                    final String[] outParts = allOut.split("\n\n");

                    softAssertions.assertThat(expectedParts.length).isGreaterThanOrEqualTo(testNum);
                    softAssertions.assertThat(outParts.length).isEqualTo(testNum);
                    final int index = testNum - 1;
                    final String expected = expectedParts[index];
                    final String out = outParts[index];

                    softAssertions.assertThat(out).isEqualTo(expected);
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
            writer.write("\n");
            writer.write("=====\n");
            writer.write(output);
            writer.write("\n\n");
        }
    }
}
