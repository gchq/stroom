package stroom.analytics.impl;

import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.util.NullSafe;

public class AnalyticUtil {

    private AnalyticUtil() {
        // Util class.
    }

    public static String getAnalyticRuleIdentity(final AnalyticRuleDoc analyticRuleDoc) {
        return NullSafe.get(
                analyticRuleDoc,
                analyticRuleDoc2 -> analyticRuleDoc2.getName() +
                        " (" +
                        analyticRuleDoc2.getUuid() +
                        ")");
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
