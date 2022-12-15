package stroom.query.language;

import java.util.List;

public class TestStructureBuilder extends AbstractQueryTest {

    @Override
    String getTestDirName() {
        return "TestStructureBuilder";
    }

    @Override
    String convert(final String input) {
        final List<Token> tokens = Tokeniser.parse(input);
        final TokenGroup tokenGroup = StructureBuilder.create(tokens);
        return tokenGroup.toTokenString(true);
    }
}
