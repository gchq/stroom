package stroom.query.common.v2;

import stroom.dashboard.expression.v1.AlphaNumericComparator;
import stroom.dashboard.expression.v1.AutoComparator;
import stroom.dashboard.expression.v1.Val;
import stroom.query.api.v2.Field;
import stroom.query.api.v2.Format.Type;

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
        return new AutoComparator();
    }
}
