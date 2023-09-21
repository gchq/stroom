package stroom.query.common.v2;

import stroom.query.api.v2.Field;
import stroom.query.api.v2.Format.Type;
import stroom.query.language.functions.AlphaNumericComparator;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValComparator;

import java.util.Comparator;

public class ComparatorFactory {

    private ComparatorFactory() {
    }

    public static Comparator<Val> create(final Field field) {
        if (field != null &&
                field.getFormat() != null &&
                Type.TEXT.equals(field.getFormat().getType())) {
            return new AlphaNumericComparator();
        }
        return new ValComparator();
    }
}
