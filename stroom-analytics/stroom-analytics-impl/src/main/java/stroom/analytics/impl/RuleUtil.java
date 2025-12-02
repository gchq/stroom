package stroom.analytics.impl;

import stroom.docstore.shared.AbstractDoc;
import stroom.util.shared.NullSafe;

public class RuleUtil {

    private RuleUtil() {
        // Util class.
    }

    public static String getRuleIdentity(final AbstractDoc doc) {
        return NullSafe.get(doc, d -> d.getName() + " (" + d.getUuid() + ")");
    }

    public static long getMin(final Long currentValue, final Long newValue) {
        if (newValue == null) {
            return 0L;
        } else if (currentValue == null) {
            return newValue;
        }
        return Math.min(currentValue, newValue);
    }

    public static long getMax(final Long currentValue, final Long newValue) {
        if (newValue == null) {
            return Long.MAX_VALUE;
        } else if (currentValue == null) {
            return newValue;
        }
        return Math.max(currentValue, newValue);
    }
}
