package stroom.query.common.v2;

import stroom.query.api.v2.ExpressionOperator;
import stroom.query.common.v2.SimpleStringExpressionParser.FieldProvider;
import stroom.query.language.token.AbstractQueryTest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

public class TestSimpleStringExpressionParser extends AbstractQueryTest {

    @Override
    protected Path getTestDir() {
        final Path dir = Paths.get("../stroom-query-common/src/test/resources/TestSimpleStringExpressionParser");
        if (!Files.isDirectory(dir)) {
            throw new RuntimeException("Test data directory not found: " + dir.toAbsolutePath());
        }
        return dir;
    }

    @Override
    protected String convert(final String input) {
        final ExpressionOperator expression = getExpression(input);
        return expression.toString();
    }

    private ExpressionOperator getExpression(final String string) {
        final FieldProvider fieldProvider = new FieldProviderImpl(
                List.of("defaultField"),
                Map.of("field1", "field1", "field2", "field2"));
        return SimpleStringExpressionParser
                .create(fieldProvider, string)
                .orElseThrow();
    }
}
