package stroom.query.common.v2;

import stroom.query.api.v2.Column;
import stroom.query.api.v2.Format.Type;
import stroom.query.language.functions.AlphaNumericComparator;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValComparator;

import java.util.Comparator;

public class ComparatorFactory {

    private ComparatorFactory() {
    }

    public static Comparator<Val> create(final Column column) {
        if (column != null &&
                column.getFormat() != null &&
                Type.TEXT.equals(column.getFormat().getType())) {
            return new AlphaNumericComparator();
        }
        return new ValComparator();
    }
}
