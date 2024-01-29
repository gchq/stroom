package stroom.query.common.v2;

import stroom.query.api.v2.Field;
import stroom.query.api.v2.Format.Type;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValComparators;

import java.util.Comparator;

public class ComparatorFactory {

    private ComparatorFactory() {
    }

    public static Comparator<Val> create(final Field field) {
        // We use case-insensitive comparators for sorting, but case-sensitive
        // ones for the boolean condition funcs like >, <, =, !=, etc.
        // Not sure this makes sense, but it is how it is.
        if (field != null &&
                field.getFormat() != null &&
                Type.TEXT.equals(field.getFormat().getType())) {
            // Pure string comparison, no comparing as doubles first.
            return ValComparators.AS_CASE_INSENSITIVE_STRING_COMPARATOR;
        } else {
            return ValComparators.GENERIC_CASE_INSENSITIVE_COMPARATOR;
        }
    }
}
