package stroom.query.common.v2;

import stroom.query.api.Column;
import stroom.query.api.Format;
import stroom.query.api.Format.Type;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValComparators;
import stroom.util.shared.NullSafe;

import java.util.Comparator;
import java.util.Objects;

public class ComparatorFactory {

    private ComparatorFactory() {
    }

    public static Comparator<Val> create(final Column column) {
        Objects.requireNonNull(column);
        // We use case-insensitive comparators for sorting, but case-sensitive
        // ones for the boolean condition funcs like >, <, =, !=, etc.
        // Not sure this makes sense, but it is how it is.
        final Format.Type formatType = NullSafe.get(
                column,
                Column::getFormat,
                Format::getType);
        // Format type trumps commonReturnType, but we may not have either
        if (Type.TEXT == formatType) {
            // Pure string comparison, no comparing as doubles first.
            return ValComparators.AS_CASE_INSENSITIVE_STRING_COMPARATOR;
        } else if (Type.NUMBER == formatType) {
            return ValComparators.AS_DOUBLE_COMPARATOR;
        } else if (Type.DATE_TIME == formatType) {
            return ValComparators.AS_LONG_COMPARATOR;
        } else {
            // Even if we have numbers mixed in with words, we want the numbers
            // sorted numerically, i.e. 3, 20, 100, foo
            // rather than 100, 20, 3, foo
            return ValComparators.AS_DOUBLE_THEN_CASE_INSENSITIVE_STRING_COMPARATOR;
        }
    }
}
