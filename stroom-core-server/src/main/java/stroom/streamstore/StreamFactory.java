/*
 * Copyright 2016 Crown Copyright
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

package stroom.streamstore;

import stroom.feed.StroomHeaderArguments;
import stroom.util.date.DateUtil;

import java.util.Map;

/**
 * Helper to build meta data classes.
 */
public final class StreamFactory {
    @Deprecated
    private static final String PERIOD_START_TIME = "periodStartTime";

    private StreamFactory() {
        // Private constructor.
    }

    public static Long getReferenceEffectiveTime(final Map<String, String> argsMap, boolean doDefault) {
        Long effectiveMs = getSafeMs(argsMap, StroomHeaderArguments.EFFECTIVE_TIME);
        if (effectiveMs != null) {
            return effectiveMs;
        }
        // This is here for backwards compatibility
        effectiveMs = getSafeMs(argsMap, PERIOD_START_TIME);
        if (effectiveMs != null) {
            return effectiveMs;
        }
        if (doDefault) {
            return System.currentTimeMillis();
        }
        return null;
    }

    public static Long getReceivedTime(final Map<String, String> argsMap, boolean doDefault) {
        Long receivedTimeMs = getSafeMs(argsMap, StroomHeaderArguments.RECEIVED_TIME);
        if (receivedTimeMs != null) {
            return receivedTimeMs;
        }
        if (doDefault) {
            return System.currentTimeMillis();
        }
        return null;
    }

    /**
     * Helper to avoid null pointers
     */
    private static Long getSafeMs(final Map<String, String> argsMap, final String key) {
        String value = argsMap.get(key);
        if (value != null) {
            try {
                return DateUtil.parseNormalDateTimeString(value);
            } catch (final RuntimeException e) {
                // Ignore if the format is wrong
            }
        }

        return null;
    }

}
