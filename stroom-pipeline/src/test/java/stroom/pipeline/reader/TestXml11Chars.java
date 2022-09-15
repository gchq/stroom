package stroom.pipeline.reader;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class TestXml11Chars {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestXml11Chars.class);

    // TODO: 15/09/2022 AT Uncomment this once createDynamicTestStream is available and
    //  remove isValid() method
//    @TestFactory
//    Stream<DynamicTest> isValid() {
//        return TestUtil.createDynamicTestStream(
//                List.of(
//                        TestCase.biOutput('a', true, true),
//                        TestCase.biOutput('1', true, true),
//                        TestCase.biOutput('.', true, true),
//                        TestCase.biOutput('Σ', true, true), // \u03a3
//                        TestCase.biOutput('\u0080', true, false),
//                        TestCase.biOutput('\u0082', true, false)),
//                testCase -> {
//                    final Character chr = testCase.getInput();
//                    return LogUtil.message("char [{}], unicode (hex): {}", chr, Integer.toHexString(chr));
//                },
//                testCase -> {
//                    final boolean isValid = testCase.getExpectedOutput()._1;
//                    final boolean isValidLiteral = testCase.getExpectedOutput()._2;
//                    doTest(testCase.getInput(), isValid, isValidLiteral);
//                });
//    }

    @Test
    void isValid() {
        doTest('a', true, true);
        doTest('1', true, true);
        doTest('.', true, true);
        doTest('Σ', true, true); // \u03a3
        doTest('\u0080', true, false);
        doTest('\u0082', true, false);
    }

    private void doTest(final char chr,
                        final boolean isValid,
                        final boolean isValidLiteral) {
        LOGGER.info("char [{}], unicode (hex): {}", chr, Integer.toHexString(chr));

        final boolean validCodePoint = Character.isValidCodePoint(chr);

        Assertions.assertThat(validCodePoint)
                .isTrue();

        Xml11Chars xml11Chars = new Xml11Chars();

        Assertions.assertThat(xml11Chars.isValid(chr))
                .isEqualTo(isValid);

        Assertions.assertThat(xml11Chars.isValidLiteral(chr))
                .isEqualTo(isValidLiteral);
    }
}
