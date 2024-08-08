/*
 * Copyright 2024 Crown Copyright
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

package stroom.receive.common;

import stroom.meta.api.AttributeMap;
import stroom.meta.api.StandardHeaderArguments;
import stroom.util.date.DateUtil;
import stroom.util.shared.string.CIKey;

/**
 * Helper to build meta data classes.
 */
public final class StreamFactory {

    @Deprecated
    private static final CIKey PERIOD_START_TIME = CIKey.ofStaticKey("periodStartTime");

    private StreamFactory() {
        // Private constructor.
    }

    public static Long getReferenceEffectiveTime(final AttributeMap attributeMap,
                                                 boolean doDefault) {
        Long effectiveMs = getSafeMs(attributeMap, StandardHeaderArguments.EFFECTIVE_TIME);
        if (effectiveMs != null) {
            return effectiveMs;
        }
        // This is here for backwards compatibility
        effectiveMs = getSafeMs(attributeMap, PERIOD_START_TIME);
        if (effectiveMs != null) {
            return effectiveMs;
        }
        if (doDefault) {
            return System.currentTimeMillis();
        }
        return null;
    }

    public static Long getReceivedTime(final AttributeMap attributeMap, boolean doDefault) {
        Long receivedTimeMs = getSafeMs(attributeMap, StandardHeaderArguments.RECEIVED_TIME);
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
    private static Long getSafeMs(final AttributeMap attributeMap, final CIKey key) {
        String value = attributeMap.get(key);
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
