package stroom.query.language;

import java.util.List;

public class TestTokeniser extends AbstractQueryTest {

    @Override
    String getTestDirName() {
        return "TestTokeniser";
    }

    @Override
    String convert(final String input) {
        final List<Token> tokens = Tokeniser.parse(input);
        final StringBuilder sb = new StringBuilder();
        for (final Token token : tokens) {
            token.append(sb, false, 0);
        }
        return sb.toString();
    }
}
