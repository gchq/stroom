package stroom.dashboard.expression.v1;

import java.util.Comparator;

public class AlphaNumericComparator implements Comparator<Val> {

    @Override
    public int compare(final Val o1, final Val o2) {
        if (o1 == null && o2 == null) {
            return 0;
        } else if (o1 == null) {
            return 1;
        } else if (o2 == null) {
            return -1;
        } else {
            // Compare as strings only.
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
