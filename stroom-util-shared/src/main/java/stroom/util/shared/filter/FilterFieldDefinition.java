/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.util.shared.filter;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Defines a field used in table filtering (quick filters).
 */
@JsonInclude(Include.NON_NULL)
public class FilterFieldDefinition {

    @JsonProperty
    private final String displayName; // e.g. My Field
    @JsonProperty
    private final String filterQualifier; // e.g. myfield
    @JsonProperty
    private final boolean defaultField;

//    public FilterFieldDefinition() {
//        displayName = null;
//        filterQualifier = null;
//        defaultField = false;
//    }

    /**
     * @param displayName     The display name or column heading for the field in the table.
     * @param filterQualifier The custom field qualifier used in the quick filter. Only use this if you want a
     *                        qualifier that is different from the display name. Should match ^[a-zA-Z0-9]+$
     * @param defaultField    True if this field does not need to be qualified in the filter
     */
    @JsonCreator
    public FilterFieldDefinition(@JsonProperty("displayName") final String displayName,
                                 @JsonProperty("filterQualifier") final String filterQualifier,
                                 @JsonProperty("defaultField") final boolean defaultField) {
        this.displayName = Objects.requireNonNull(displayName);
        this.filterQualifier = Objects.requireNonNull(filterQualifier);
        this.defaultField = defaultField;
    }

    /**
     * @param displayName The display name or column heading for the field in the table. The lowercase
     *                    form of the display name (without punctuation) will be used for the field
     *                    qualifier, e.g. First Name => firstname
     */
    public static FilterFieldDefinition qualifiedField(final String displayName) {
        return new FilterFieldDefinition(displayName, toQualifiedName(displayName), false);
    }

    /**
     * Creates a field that has to be qualified, e.g. 'type:error'
     *
     * @param displayName     The display name or column heading for the field in the table.
     * @param filterQualifier The custom field qualifier used in the quick filter. Only use this if you want a
     *                        qualifier that is different from the display name. Should match ^[a-zA-Z0-9]+$
     */
    public static FilterFieldDefinition qualifiedField(final String displayName,
                                                       final String filterQualifier) {
        return new FilterFieldDefinition(displayName, filterQualifier, false);
    }

    /**
     * @param displayName The display name or column heading for the field in the table. The lowercase
     *                    form of the display name (without punctuation) will be used for the field
     *                    qualifier, e.g. First Name => firstname
     */
    public static FilterFieldDefinition defaultField(final String displayName) {
        return new FilterFieldDefinition(displayName, toQualifiedName(displayName), true);
    }

    /**
     * Creates a field that is a default field which does not havee to be qualified. You can have multiple
     * default fields. If there are multiple then the predicate for each default field are OR'd together.
     *
     * @param displayName     The display name or column heading for the field in the table.
     * @param filterQualifier The custom field qualifier used in the quick filter. Only use this if you want a
     *                        qualifier that is different from the display name. Should match ^[a-zA-Z0-9]+$
     */
    public static FilterFieldDefinition defaultField(final String displayName,
                                                     final String filterQualifier) {
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
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
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
        return "[" + displayName + " (" + filterQualifier + ") " + (defaultField
                ? "DEFAULT"
                : "QUALIFIED") + "]";
    }
}
