package stroom.util.shared.filter;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@JsonInclude(Include.NON_NULL)
public class FilterFieldDefinition {

    @JsonProperty
    private final String displayName; // e.g. My Field
    @JsonProperty
    private final String filterQualifier; // e.g. myfield
    @JsonProperty
    private final boolean defaultField;

    public FilterFieldDefinition(@JsonProperty("displayName") final String displayName,
                                 @JsonProperty("filterQualifier") final String filterQualifier,
                                 @JsonProperty("defaultField") final boolean defaultField) {
        this.displayName = Objects.requireNonNull(displayName);
        this.filterQualifier = Objects.requireNonNull(filterQualifier);
        this.defaultField = defaultField;
    }

    public static FilterFieldDefinition qualifiedField(final String displayName) {
        return new FilterFieldDefinition(displayName, toQualifiedName(displayName), false);
    }
    public static FilterFieldDefinition qualifiedField(final String displayName, final String filterQualifier) {
        return new FilterFieldDefinition(displayName, filterQualifier, false);
    }

    public static FilterFieldDefinition defaultField(final String displayName) {
        return new FilterFieldDefinition(displayName, toQualifiedName(displayName), true);
    }

    public static FilterFieldDefinition defaultField(final String displayName, final String filterQualifier) {
        return new FilterFieldDefinition(displayName, filterQualifier, true);
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getFilterQualifier() {
        return filterQualifier;
    }

    public boolean isDefaultField() {
        return defaultField;
    }

    private static String toQualifiedName(final String displayName) {
        // "My Field (something)" => "myfieldsomething"
        final String qualifiedName = displayName.chars()
                .mapToObj(i -> {
                    final char chr = (char) i;
                    final Optional<String> optStr = Character.isLetterOrDigit(chr)
                            ? Optional.of(String.valueOf(chr))
                            : Optional.empty();
                    return optStr;
                })
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.joining());

        return qualifiedName
                .toLowerCase()
                .trim();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final FilterFieldDefinition that = (FilterFieldDefinition) o;
        return defaultField == that.defaultField &&
                Objects.equals(displayName, that.displayName) &&
                Objects.equals(filterQualifier, that.filterQualifier);
    }

    @Override
    public int hashCode() {
        return Objects.hash(displayName, filterQualifier, defaultField);
    }

    @Override
    public String toString() {
        return "FieldDefinition{" +
                "displayName='" + displayName + '\'' +
                ", filterQualifier='" + filterQualifier + '\'' +
                ", defaultField=" + defaultField +
                '}';
    }
}
