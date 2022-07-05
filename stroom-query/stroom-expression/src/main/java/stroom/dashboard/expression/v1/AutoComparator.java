package stroom.dashboard.expression.v1;

import java.util.Comparator;

public class AutoComparator implements Comparator<Val> {

    @Override
    public int compare(final Val o1, final Val o2) {
        if (o1 == null && o2 == null) {
            return 0;
        } else if (o1 == null) {
            return 1;
        } else if (o2 == null) {
            return -1;
        } else {
            // Try to compare values as doubles so that numeric strings are sorted appropriately.
            // If only one of the values can be converted to a double it will always come first.
            // If neither value can be converted to a double then the values will be compared alphanumerically as
            // strings.
            final Double d1 = o1.toDouble();
            final Double d2 = o2.toDouble();

            if (d1 != null) {
                if (d2 != null) {
                    return Double.compare(d1, d2);
                } else {
                    return -1;
                }
            } else if (d2 != null) {
                return 1;
            }

            // If we couldn't compare as doubles then compare as strings.
            final String str1 = o1.toString();
            final String str2 = o2.toString();

            if (str1 != null) {
                if (str2 != null) {
                    return str1.compareToIgnoreCase(str2);
                } else {
                    return -1;
                }
            } else if (str2 != null) {
                return 1;
            }

            return 0;
        }
    }
}
