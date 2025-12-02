package stroom.index.lucene.analyser;

import stroom.query.api.datasource.AnalyzerType;
import stroom.test.common.util.test.StroomUnitTest;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import io.vavr.Tuple;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

class TestAnalyzerFactory extends StroomUnitTest {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestAnalyzerFactory.class);

    @TestFactory
    Stream<DynamicTest> test() {
        return List
                .of(Tuple.of(AnalyzerType.KEYWORD, "One Two Three", List.of("One Two Three"), true),
                        Tuple.of(AnalyzerType.KEYWORD, "One Two Three", List.of("one two three"), false),
                        Tuple.of(AnalyzerType.ALPHA, "One1 Two2 Three 3", List.of("One", "Two", "Three"), true),
                        Tuple.of(AnalyzerType.ALPHA, "One1 Two2 Three 3", List.of("one", "two", "three"), false),
                        Tuple.of(AnalyzerType.ALPHA_NUMERIC,
                                "One1 Two2 Three 3",
                                List.of("One1", "Two2", "Three", "3"),
                                true),
                        Tuple.of(AnalyzerType.ALPHA_NUMERIC,
                                "One1 Two2 Three 3",
                                List.of("one1", "two2", "three", "3"),
                                false),
                        Tuple.of(AnalyzerType.NUMERIC, "One11 Two22 Three 33", List.of("11", "22", "33"), true),
                        Tuple.of(AnalyzerType.NUMERIC, "One11 Two22 Three 33", List.of("11", "22", "33"), false),
                        Tuple.of(AnalyzerType.STANDARD,
                                "aaa.com is great! He is a good man and a legend.",
                                List.of("aaa.com", "is", "great", "he", "is", "a", "good", "man", "and", "a", "legend"),
                                false),
                        Tuple.of(AnalyzerType.STANDARD,
                                "aaa.com is great! He is a good man and a legend.",
                                List.of("aaa.com", "is", "great", "he", "is", "a", "good", "man", "and", "a", "legend"),
                                true),
                        Tuple.of(AnalyzerType.WHITESPACE,
                                "aaa.com is great! He is a good man and a legend.",
                                List.of("aaa.com",
                                        "is",
                                        "great!",
                                        "He",
                                        "is",
                                        "a",
                                        "good",
                                        "man",
                                        "and",
                                        "a",
                                        "legend."),
                                false),
                        Tuple.of(AnalyzerType.WHITESPACE,
                                "aaa.com is great! He is a good man and a legend.",
                                List.of("aaa.com",
                                        "is",
                                        "great!",
                                        "He",
                                        "is",
                                        "a",
                                        "good",
                                        "man",
                                        "and",
                                        "a",
                                        "legend."),
                                true),
                        Tuple.of(AnalyzerType.STOP,
                                "aaa.com is great! He is a good man and a legend.",
                                List.of("aaa.com", "great", "he", "good", "man", "legend"),
                                false),
                        Tuple.of(AnalyzerType.STOP,
                                "aaa.com is great! He is a good man and a legend.",
                                List.of("aaa.com", "great", "he", "good", "man", "legend"),
                                true))
                .stream()
                .map(tuple3 -> {
                    final boolean isCaseSensitive = tuple3._4;
                    final AnalyzerType analyzerType = tuple3._1;
                    final String input = tuple3._2;

                    return DynamicTest.dynamicTest(analyzerType.getDisplayValue() +
                                    "(" +
                                    isCaseSensitive +
                                    ") - \"" +
                                    input +
                                    "\"",
                            () -> {
                                doTest(AnalyzerFactory.create(analyzerType, isCaseSensitive), tuple3._2, tuple3._3);
                            });
                });
    }

    private void doTest(final Analyzer analyzer, final String input, final List<String> expectedTokens)
            throws IOException {
        final List<String> result = new ArrayList<>();
        final TokenStream tokenStream = analyzer.tokenStream("MyField", input);
        final CharTermAttribute attr = tokenStream.addAttribute(CharTermAttribute.class);
        tokenStream.reset();
        while (tokenStream.incrementToken()) {
            result.add(attr.toString());
        }

        LOGGER.info("{} - {} - {}", analyzer.getClass().getSimpleName(), input, result);

        Assertions.assertThat(result).containsExactlyElementsOf(expectedTokens);
    }

}
