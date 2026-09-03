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

package stroom.analytics.shared;

import stroom.docref.HasDisplayValue;

/**
 * A rule's status denotes how reliable it is.
 */
public enum AnalyticRuleStatus implements HasDisplayValue {
    /**
     * An early-stage rule that may be incomplete. Expect more false positives.
     */
    EXPERIMENTAL("Experimental"),
    /**
     * More mature than experimental rules. Actively being validated in real environments.
     * Expect some false positives.
     */
    TESTING("Testing"),
    /**
     * Considered production-ready. Has been thoroughly tested across multiple environments.
     * Expect a reasonable false positive rate.
     */
    STABLE("Stable"),
    /**
     * An outdated or superseded rule that may rely on old techniques or assumptions.
     * Generally avoid using these in production.
     */
    DEPRECATED("Deprecated"),
    ;

    private final String displayValue;

    AnalyticRuleStatus(final String displayValue) {
        this.displayValue = displayValue;
    }

    @Override
    public String getDisplayValue() {
        return displayValue;
    }
}
