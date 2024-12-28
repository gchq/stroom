package stroom.analytics.impl;

import stroom.docstore.shared.Doc;
import stroom.util.NullSafe;

public class RuleUtil {

    private RuleUtil() {
        // Util class.
    }

    public static String getRuleIdentity(final Doc doc) {
        return NullSafe.get(doc, d -> d.getName() + " (" + d.getUuid() + ")");
    }

    public static long getMin(Long currentValue, Long newValue) {
        if (newValue == null) {
            return 0L;
        } else if (currentValue == null) {
            return newValue;
        }
        return Math.min(currentValue, newValue);
    }

    public static long getMax(Long currentValue, Long newValue) {
        if (newValue == null) {
            return Long.MAX_VALUE;
        } else if (currentValue == null) {
            return newValue;
        }
        return Math.max(currentValue, newValue);
    }
}
