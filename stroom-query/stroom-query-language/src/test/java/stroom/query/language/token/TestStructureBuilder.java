package stroom.query.language.token;

import stroom.query.api.token.Token;
import stroom.query.api.token.TokenGroup;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class TestStructureBuilder extends AbstractQueryTest {

    @Override
    protected Path getTestDir() {
        final Path dir = Paths.get("../stroom-query-language/src/test/resources/TestStructureBuilder");
        if (!Files.isDirectory(dir)) {
            throw new RuntimeException("Test data directory not found: " + dir.toAbsolutePath());
        }
        return dir;
    }

    @Override
    protected String convert(final String input) {
        final List<Token> tokens = Tokeniser.parse(input);
        final TokenGroup tokenGroup = StructureBuilder.create(tokens);
        return tokenGroup.toTokenString(true);
    }
}
