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

package stroom.pipeline.stepping.read;

import stroom.pipeline.filter.PersistedXPathFilterMatcher;
import stroom.pipeline.shared.SharedElementData;
import stroom.pipeline.shared.stepping.SteppingFilterSettings;
import stroom.util.shared.Indicators;
import stroom.util.shared.NullSafe;
import stroom.util.shared.OutputState;
import stroom.util.shared.Severity;

/**
 * Applies a {@link SteppingFilterSettings} to a single element's persisted {@link SharedElementData},
 * mirroring the live {@code SAXEventRecorder.filterMatches} semantics but reading from captured data
 * instead of the in-flight recorder.
 * <p>
 * Within one element the checks are OR'd (any match =&gt; the element matches): skip-to-severity, then
 * skip-to-output (EMPTY / NOT_EMPTY, evaluated from {@link SharedElementData#isHasOutput()} which is the
 * persisted form of the recorder's {@code maxElementDepth > 1}), then XPath filters.
 * <p>
 * XPath filter evaluation over persisted data (re-parsing the stored output XML) is not yet implemented;
 * it will be added when capture is wired up and real serialized output is available to exercise it.
 */
public class PersistedFilterEvaluator {

    /**
     * @return true if this element's captured data matches the element's active filter settings.
     */
    public boolean matches(final SharedElementData data,
                           final SteppingFilterSettings settings,
                           final long metaId,
                           final long recordIndex) {
        if (settings == null || !settings.isFilterApplied()) {
            return false;
        }

        // Skip to a severity: match if the element's captured max severity is at least the target.
        final Severity skipToSeverity = settings.getSkipToSeverity();
        if (skipToSeverity != null && data != null) {
            final Indicators indicators = data.getIndicators();
            final Severity maxSeverity = indicators == null ? null : indicators.getMaxSeverity();
            if (maxSeverity != null && maxSeverity.greaterThanOrEqual(skipToSeverity)) {
                return true;
            }
        }

        // Skip to (no) output: match on whether the element actually produced output.
        final OutputState skipToOutput = settings.getSkipToOutput();
        if (skipToOutput != null && data != null) {
            final boolean hasOutput = data.isHasOutput();
            if (hasOutput && OutputState.NOT_EMPTY.equals(skipToOutput)) {
                return true;
            }
            if (!hasOutput && OutputState.EMPTY.equals(skipToOutput)) {
                return true;
            }
        }

        if (NullSafe.hasItems(settings.getFilters()) && data != null) {
            // XPath filters run against the element's persisted output XML, re-parsed and matched using the
            // same machinery as live stepping.
            return PersistedXPathFilterMatcher.matches(
                    data.getOutput(), settings.getFilters(), metaId, recordIndex);
        }

        return false;
    }
}
