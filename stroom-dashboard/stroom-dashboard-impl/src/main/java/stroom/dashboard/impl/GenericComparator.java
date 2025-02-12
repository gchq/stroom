package stroom.dashboard.impl;

import java.util.Comparator;

public class GenericComparator implements Comparator<String> {

    @Override
    public int compare(final String o1, final String o2) {
        try {
            // See if we can get num from o1.
            final long l1 = Long.parseLong(o1);
            try {
                // See if we can get num from o2.
                final long l2 = Long.parseLong(o2);
                // o2 is a number as well so compare.
                return Long.compare(l1, l2);
            } catch (final NumberFormatException e) {
                // o1 is a number so put it before o2.
                return -1;
            }

        } catch (final NumberFormatException e) {
            try {
                // See if we can get num from o2.
                Long.parseLong(o2);
                // o2 is a number so put o1 after.
                return 1;

            } catch (final NumberFormatException e2) {
                // Both failed to parse as numbers so compare as strings.
                return o1.compareToIgnoreCase(o2);
            }
        }
    }
}
