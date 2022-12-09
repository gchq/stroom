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

public class TestTokeniser {

    // If we are editing the input or behaviour then we don't want to test the validity but instead create the expected
    // test output. 
    private static final boolean CREATE_MODE = true;
    
    @TestFactory
    Stream<DynamicTest> buildTests() throws IOException {
        final Path dir = TestFileUtil.getTestResourcesDir().resolve("TestTokeniser");
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

    private void testInput(final String input,
                           final Path outFile,
                           final Path expectedFile,
                           final int testNum) {
        System.out.println("Testing input: " + input);
        SoftAssertions.assertSoftly(softAssertions -> {
            try {
                final Tokeniser tokeniser = new Tokeniser();
                final TokenGroup tokenGroup = tokeniser.extractTokens(input);
                final String tokenString = tokenGroup.toTokenString(true);
                appendToFile(outFile, input, tokenString);

                if (CREATE_MODE) {
                    appendToFile(expectedFile, input, tokenString);
                    
                } else {
                    // If we aren't creating tests then actually test.
                    final String allExpected = Files.readString(expectedFile);
                    final String allOut = Files.readString(outFile);

                    final String[] expectedParts = allExpected.split("\n\n\n");
                    final String[] outParts = allOut.split("\n\n\n");

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

//    @Test
//    void test() {
//        test("before \"test of inner \\\" strings \" after",
//                "   <UNKNOWN>before</UNKNOWN>\n" +
//                        "   <WHITESPACE> </WHITESPACE>\n" +
//                        "   <DOUBLE_QUOTED_STRING>\"test of inner \\\" strings \"</DOUBLE_QUOTED_STRING>\n" +
//                        "   <WHITESPACE> </WHITESPACE>\n" +
//                        "   <UNKNOWN>after</UNKNOWN>");
//        test("\"test of inner \\\" strings \" after",
//                "<DOUBLE_QUOTED_STRING>\"test of inner \\\" strings \"<DOUBLE_QUOTED_STRING>\n" +
//                        "<UNKNOWN> after<UNKNOWN>");
//        test(" \"test of inner \\\" strings \" after ",
//                "<UNKNOWN> <UNKNOWN>\n" +
//                        "<DOUBLE_QUOTED_STRING>\"test of inner \\\" strings \"<DOUBLE_QUOTED_STRING>\n" +
//                        "<UNKNOWN> after <UNKNOWN>");
//        test("before \"test of inner \\\" strings \"",
//                "<UNKNOWN>before <UNKNOWN>\n" +
//                        "<DOUBLE_QUOTED_STRING>\"test of inner \\\" strings \"<DOUBLE_QUOTED_STRING>");
//        test("before \"test of inner \\\" strings \" ",
//                "<UNKNOWN>before <UNKNOWN>\n" +
//                        "<DOUBLE_QUOTED_STRING>\"test of inner \\\" strings \"<DOUBLE_QUOTED_STRING>\n" +
//                        "<UNKNOWN> <UNKNOWN>");
//
//
//        test("before 'test of inner \\' strings ' after",
//                "<UNKNOWN>before <UNKNOWN>\n" +
//                        "<SINGLE_QUOTED_STRING>'test of inner \\' strings '<SINGLE_QUOTED_STRING>\n" +
//                        "<UNKNOWN> after<UNKNOWN>");
//        test("'test of inner \\' strings ' after",
//                "<SINGLE_QUOTED_STRING>'test of inner \\' strings '<SINGLE_QUOTED_STRING>\n" +
//                        "<UNKNOWN> after<UNKNOWN>");
//        test(" 'test of inner \\' strings ' after ",
//                "<UNKNOWN> <UNKNOWN>\n" +
//                        "<SINGLE_QUOTED_STRING>'test of inner \\' strings '<SINGLE_QUOTED_STRING>\n" +
//                        "<UNKNOWN> after <UNKNOWN>");
//        test("before 'test of inner \\' strings '",
//                "<UNKNOWN>before <UNKNOWN>\n" +
//                        "<SINGLE_QUOTED_STRING>'test of inner \\' strings '<SINGLE_QUOTED_STRING>");
//        test("before 'test of inner \\' strings ' ",
//                "<UNKNOWN>before <UNKNOWN>\n" +
//                        "<SINGLE_QUOTED_STRING>'test of inner \\' strings '<SINGLE_QUOTED_STRING>\n" +
//                        "<UNKNOWN> <UNKNOWN>");
//
//        test("table | where this == that | and foo == 'test of inner \\' strings ' ",
//                "<UNKNOWN>table <UNKNOWN>\n" +
//                        "<PIPE>|<PIPE>\n" +
//                        "<UNKNOWN> <UNKNOWN>\n" +
//                        "<WHERE>where<WHERE>\n" +
//                        "<UNKNOWN> this <UNKNOWN>\n" +
//                        "<EQUALS>==<EQUALS>\n" +
//                        "<UNKNOWN> that <UNKNOWN>\n" +
//                        "<PIPE>|<PIPE>\n" +
//                        "<UNKNOWN> <UNKNOWN>\n" +
//                        "<AND>and<AND>\n" +
//                        "<UNKNOWN> foo <UNKNOWN>\n" +
//                        "<EQUALS>==<EQUALS>\n" +
//                        "<UNKNOWN> <UNKNOWN>\n" +
//                        "<SINGLE_QUOTED_STRING>'test of inner \\' strings '<SINGLE_QUOTED_STRING>\n" +
//                        "<UNKNOWN> <UNKNOWN>");
//
//        test("Test Index\n" +
//                        "| where UserId == user5 and Description == e0567\n" +
//                        "| and EventTime <= 2000-01-01T00:00:00.000Z\n" +
//                        "| and EventTime >= 2016-01-02T00:00:00.000Z\n",
//                "<UNKNOWN>Test Index\n" +
//                        "<UNKNOWN>\n" +
//                        "<PIPE>|<PIPE>\n" +
//                        "<UNKNOWN> <UNKNOWN>\n" +
//                        "<WHERE>where<WHERE>\n" +
//                        "<UNKNOWN> UserId <UNKNOWN>\n" +
//                        "<EQUALS>==<EQUALS>\n" +
//                        "<UNKNOWN> user5 <UNKNOWN>\n" +
//                        "<AND>and<AND>\n" +
//                        "<UNKNOWN> Description <UNKNOWN>\n" +
//                        "<EQUALS>==<EQUALS>\n" +
//                        "<UNKNOWN> e0567\n" +
//                        "<UNKNOWN>\n" +
//                        "<PIPE>|<PIPE>\n" +
//                        "<UNKNOWN> <UNKNOWN>\n" +
//                        "<AND>and<AND>\n" +
//                        "<UNKNOWN> EventTime <UNKNOWN>\n" +
//                        "<LESS_THAN_OR_EQUAL_TO><=<LESS_THAN_OR_EQUAL_TO>\n" +
//                        "<UNKNOWN> 2000-01-01T00:00:00.000Z\n" +
//                        "<UNKNOWN>\n" +
//                        "<PIPE>|<PIPE>\n" +
//                        "<UNKNOWN> <UNKNOWN>\n" +
//                        "<AND>and<AND>\n" +
//                        "<UNKNOWN> EventTime <UNKNOWN>\n" +
//                        "<GREATER_THAN_OR_EQUAL_TO>>=<GREATER_THAN_OR_EQUAL_TO>\n" +
//                        "<UNKNOWN> 2016-01-02T00:00:00.000Z\n" +
//                        "<UNKNOWN>");
//    }
//
//    private void test(final String input, final String expected) {
//        final Tokeniser tokeniser = new Tokeniser();
//
//        // Break the string into quoted text blocks.
//        final TokenGroup tokenGroup = tokeniser.extractTokens(input);
//
//        System.out.println("INPUT = \n" + input);
////        final StringBuilder sb = new StringBuilder();
////        for (final Token token : tokens) {
////            sb.append("<" + token.tokenType + ">" + token + "<" + token.tokenType + ">\n");
////        }
////        if (sb.length() > 0) {
////            sb.setLength(sb.length() - 1);
////        }
//
//        final String debug = tokenGroup.toDebugString();
//        System.out.println("OUTPUT = \n" + debug);
//
//        assertThat(debug).isEqualTo("<GROUP>\n" + expected + "\n</GROUP>");
//    }
}
