package stroom.query.common.v2;

import stroom.query.common.v2.SimpleStringExpressionParser.FieldProvider;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class FieldProviderImpl implements FieldProvider {

    private final List<String> defaultFields;
    private final Map<String, String> qualifiedFields;

    public FieldProviderImpl(final List<String> defaultFields, final Map<String, String> qualifiedFields) {
        this.defaultFields = defaultFields;
        this.qualifiedFields = qualifiedFields;
    }

    public FieldProviderImpl(final List<String> defaultFields, final List<String> qualifiedFields) {
        this.defaultFields = defaultFields;
        this.qualifiedFields = qualifiedFields
                .stream()
                .collect(Collectors.toMap(name -> name.toLowerCase(Locale.ROOT), name -> name));
    }

    @Override
    public List<String> getDefaultFields() {
        return defaultFields;
    }

    @Override
    public Optional<String> getQualifiedField(final String string) {
        return Optional.ofNullable(qualifiedFields.get(string.toLowerCase(Locale.ROOT)));
    }
}
