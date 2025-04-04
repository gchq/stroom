package stroom.query.language.token;

import stroom.query.api.token.QuotedStringUtil;
import stroom.test.common.TestUtil;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.stream.Stream;

class TestQuotedStringUtil {

    @TestFactory
    Stream<DynamicTest> test() {
        return TestUtil.buildDynamicTestStream()
                .withInputAndOutputType(String.class)
                .withTestFunction(testCase -> {
                    final String input = testCase.getInput();
                    return QuotedStringUtil.unescape(
                            input.toCharArray(),
                            0,
                            input.length() - 1,
                            '\\');
                })
                .withSimpleEqualityAssertion()
                .addCase("\"foo\"", "foo")
                .addCase("""
                                "foo \\"bar"\
                                """, // "foo \"bar"
                        """
                                foo "bar\
                                """)
                .addCase("""
                                'foo \\'bar'\
                                """, // 'foo \'bar'
                        """
                                foo 'bar\
                                """)
                .addCase("""
                                "\\\\d"\
                                """, // "\\d"
                        """
                                \\d\
                                """)
                .addCase("""
                                "\\\\"\
                                """, // "\\"
                        """
                                \\\
                                """)
                .build();
    }

}
