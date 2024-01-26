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
        if (field != null &&
                field.getFormat() != null &&
                Type.TEXT.equals(field.getFormat().getType())) {
            // Pure string comparison, no comparing as doubles first.
            return ValComparators.AS_STRING_COMPARATOR;
        } else {
            return ValComparators.GENERIC_COMPARATOR;
        }
    }
}
