package stroom.query.api;

import stroom.query.api.token.NRegex.Branch;
import stroom.query.api.token.NRegex.CharPredicates;
import stroom.query.api.token.NRegex.EndMatcher;
import stroom.query.api.token.NRegex.GreedyMatcher;
import stroom.query.api.token.NRegex.Match;
import stroom.query.api.token.NRegex.Matcher;
import stroom.query.api.token.NRegex.RecordEndMatcher;
import stroom.query.api.token.NRegex.RecordStartMatcher;
import stroom.query.api.token.NRegex.ReluctantMatcher;
import stroom.query.api.token.NRegex.SingleMatcher;
import stroom.query.api.token.NRegex.StringMatcher;
import stroom.query.api.token.NRegex.TerminalMatcher;
import stroom.test.common.TestUtil;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public class TestNRegex {

        private static final int ITERATIONS = 100000000;
//    private static final int ITERATIONS = 1;

    private static final Matcher MATCHER;
    private static Match match = new Match();

    static {
        Matcher m1 = new SingleMatcher(CharPredicates.whitespace(), new TerminalMatcher());
        Matcher m2 = new SingleMatcher(CharPredicates.eq('('), new TerminalMatcher());
        Matcher m3 = new EndMatcher();

        Matcher branch = new Branch(new Matcher[]{m3, m2, m1});
        RecordEndMatcher end = new RecordEndMatcher(branch, match);

        Matcher m5 = new StringMatcher("keyword", end);
        RecordStartMatcher start = new RecordStartMatcher(m5, match);

        Matcher m6 = GreedyMatcher.star(CharPredicates.whitespace(), start);
        Matcher m7 = ReluctantMatcher.star(CharPredicates.any(), new SingleMatcher(CharPredicates.ne('='),
                GreedyMatcher.plus(CharPredicates.whitespace(), start)));
        Matcher m8 = ReluctantMatcher.star(CharPredicates.any(), new SingleMatcher(CharPredicates.eq(')'), start));

        MATCHER = new Branch(new Matcher[]{m6, m7, m8});
    }


    @Test
    void testNRegex() {
        // Equivalent of regex `(^\\s*|[^=]\\s+|\\))(keyword)(\\s|\\(|$)`
//        final Matcher matcher = NRegex
//                .builder()
//                .choice(choice -> choice
//                        .add(pattern -> pattern.anchorStart(true).star(CharPredicates.whitespace()))
//                        .add(pattern -> pattern.one(CharPredicates.ne('=')).plus(CharPredicates.whitespace()))
//                        .add(pattern -> pattern.one(CharPredicates.eq(')'))))
//                .add(pattern -> pattern.string("keyword"))
//                .choice(choice -> choice
//                        .add(pattern -> pattern.one(CharPredicates.whitespace()))
//                        .add(pattern -> pattern.one(CharPredicates.eq('(')))
//                        .add(pattern -> pattern.anchorEnd(true)))
//                .build();


        char[] c1 = "keyword".toCharArray();
        char[] c2 = "  keyword".toCharArray();
        char[] c3 = "  keyword foo".toCharArray();
        char[] c4 = "f  keyword".toCharArray();
        char[] c5 = "f  keyword bar".toCharArray();
        char[] c6 = ")keyword".toCharArray();
        char[] c7 = ")keyword bar".toCharArray();
        for (int i = 0; i < ITERATIONS; i++) {
            test(c1, 0, 7);
            test(c2, 2, 7);
            test(c3, 2, 7);
            test(c4, 3, 7);
            test(c5, 3, 7);
            test(c6, 1, 7);
            test(c7, 1, 7);
        }
    }

    private void test(final char[] chars, int off, int len) {
        final boolean found = MATCHER.match(chars);
        assertThat(found).isTrue();
//        final Match match = optional.get();
//        assertThat(match.children().length).isEqualTo(3);
//        final Match child = match.children()[1];
        assertThat(match.start).isEqualTo(off);
        assertThat(match.end).isEqualTo(off + len);
    }


    final Pattern PATTERN = Pattern.compile("(^\\s*|[^=]\\s+|\\))(keyword)(\\s|\\(|$)");

    @Test
    void testJavaRegex() {
        for (int i = 0; i < ITERATIONS; i++) {
            testregex("previous text f  keyword bar", 17, 7);
            testregex("previous text )keyword bar", 15, 7);


            testregex("keyword", 0, 7);
            testregex("  keyword", 2, 7);
            testregex("  keyword foo", 2, 7);
            testregex("f  keyword", 3, 7);
            testregex("f  keyword bar", 3, 7);
            testregex(")keyword", 1, 7);
            testregex(")keyword bar", 1, 7);


        }
    }

    private void testregex(final String string, int off, int len) {
        final java.util.regex.Matcher matcher = PATTERN.matcher(string);
        assertThat(matcher.find()).isTrue();
        assertThat(matcher.start(2)).isEqualTo(off);
        assertThat(matcher.end(2)).isEqualTo(off + len);
    }

    @TestFactory
    Stream<DynamicTest> testDynamicNRegex() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(String.class)
                .withOutputType(Match.class)
                .withTestFunction(testCase -> {
                    char[] chars = testCase.getInput().toCharArray();
                    for (int i = 0; i < ITERATIONS; i++) {
                        assertThat(MATCHER.match(chars)).isTrue();
                    }

                    assertThat(MATCHER.match(chars)).isTrue();
                    return new Match(match.start, match.end);
                })
                .withSimpleEqualityAssertion()
                .addCase("keyword", new Match(0, 7))
                .addCase("  keyword", new Match(2, 9))
                .addCase("  keyword foo", new Match(2, 9))
                .addCase("f  keyword", new Match(3, 10))
                .addCase("f  keyword bar", new Match(3, 10))
                .addCase(")keyword", new Match(1, 8))
                .addCase(")keyword bar", new Match(1, 8))
                .addCase("previous text f  keyword bar", new Match(17, 24))
                .addCase("previous text )keyword bar", new Match(15, 22))
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testDynamicJavaRegex() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(String.class)
                .withOutputType(Match.class)
                .withTestFunction(testCase -> {
                    for (int i = 0; i < ITERATIONS; i++) {
                        final java.util.regex.Matcher matcher = PATTERN.matcher(testCase.getInput());
                        assertThat(matcher.find()).isTrue();
                    }

                    final java.util.regex.Matcher matcher = PATTERN.matcher(testCase.getInput());
                    assertThat(matcher.find()).isTrue();
                    return new Match(matcher.start(2), matcher.end(2));
                })
                .withSimpleEqualityAssertion()
                .addCase("keyword", new Match(0, 7))
                .addCase("  keyword", new Match(2, 9))
                .addCase("  keyword foo", new Match(2, 9))
                .addCase("f  keyword", new Match(3, 10))
                .addCase("f  keyword bar", new Match(3, 10))
                .addCase(")keyword", new Match(1, 8))
                .addCase(")keyword bar", new Match(1, 8))
                .addCase("previous text f  keyword bar", new Match(17, 24))
                .addCase("previous text )keyword bar", new Match(15, 22))
                .build();
    }
}
