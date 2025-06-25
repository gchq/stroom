package stroom.query.language.token;

import stroom.query.api.token.NonRegexTokeniser;
import stroom.query.api.token.Token;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class TestNonRegexTokeniser extends AbstractQueryTest {

    @Override
    protected Path getTestDir() {
        final Path dir = Paths.get("../stroom-query-language/src/test/resources/TestNonRegexTokeniser");
        if (!Files.isDirectory(dir)) {
            throw new RuntimeException("Test data directory not found: " + dir.toAbsolutePath());
        }
        return dir;
    }

    @Override
    protected String convert(final String input) {
//        final List<Token> tokens = NonRegexTokeniser.parse(input);
        final StringBuilder sb = new StringBuilder();
//        for (final Token token : tokens) {
//            token.append(sb, false, 0);
//        }
        return sb.toString();
    }
}
