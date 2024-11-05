package stroom.query.common.v2;

import stroom.query.common.v2.SimpleStringExpressionParser.FieldProvider;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class SingleFieldProvider implements FieldProvider {

    private final List<String> defaultFields;

    public SingleFieldProvider(final String defaultField) {
        this.defaultFields = Collections.singletonList(defaultField);
    }

    @Override
    public List<String> getDefaultFields() {
        return defaultFields;
    }

    @Override
    public Optional<String> getQualifiedField(final String string) {
        return Optional.empty();
    }
}
