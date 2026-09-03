/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.analytics.impl;

import stroom.analytics.shared.AbstractAnalyticRuleDoc;
import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.analytics.shared.AnalyticRuleLevel;
import stroom.analytics.shared.AnalyticRuleStatus;
import stroom.docstore.shared.AbstractDoc;
import stroom.util.shared.NullSafe;

public class RuleUtil {

    private RuleUtil() {
        // Util class.
    }

    public static String getRuleIdentity(final AbstractDoc doc) {
        return NullSafe.get(doc, d -> d.getName() + " (" + d.getUuid() + ")");
    }

    /**
     * The detailed description to put on a detection, which is the rule's documentation only where the rule
     * says to include it.
     * <p>
     * Detections can leave Stroom, e.g. by email, so a rule that has been told not to include its
     * documentation must not include it whichever way the rule is processed. Every site that builds a
     * {@link Detection} from a doc must use this rather than reading the description directly.
     * </p>
     * <p>
     * Only {@link AnalyticRuleDoc} carries the setting. Anything else, e.g. a report, has no way to turn the
     * documentation off and so always includes it.
     * </p>
     *
     * @return The doc's documentation, or null where it is not to be included.
     */
    public static String getDetailedDescription(final AbstractAnalyticRuleDoc doc) {
        if (doc instanceof final AnalyticRuleDoc analyticRuleDoc
            && !analyticRuleDoc.isIncludeRuleDocumentation()) {
            return null;
        }
        return NullSafe.get(doc, AbstractAnalyticRuleDoc::getDescription);
    }

    /**
     * The level to put on a detection, which denotes the severity of the rule that produced it.
     * <p>
     * Only {@link AnalyticRuleDoc} carries a level. Anything else, e.g. a report, has none.
     * </p>
     *
     * @return The display value of the doc's level, or null where it has none.
     */
    public static String getLevel(final AbstractAnalyticRuleDoc doc) {
        return doc instanceof final AnalyticRuleDoc analyticRuleDoc
                ? NullSafe.get(analyticRuleDoc.getLevel(), AnalyticRuleLevel::getDisplayValue)
                : null;
    }

    /**
     * The status to put on a detection, which denotes how reliable the rule that produced it is.
     * <p>
     * Only {@link AnalyticRuleDoc} carries a status. Anything else, e.g. a report, has none.
     * </p>
     *
     * @return The display value of the doc's status, or null where it has none.
     */
    public static String getStatus(final AbstractAnalyticRuleDoc doc) {
        return doc instanceof final AnalyticRuleDoc analyticRuleDoc
                ? NullSafe.get(analyticRuleDoc.getStatus(), AnalyticRuleStatus::getDisplayValue)
                : null;
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
