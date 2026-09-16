/*
 * Copyright 2026 Crown Copyright
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

package stroom.proxy.app.pipeline.stage.forward;

import java.time.Duration;
import java.util.Objects;

/**
 * How long a destination keeps trying and how it backs off between attempts
 * ({@code designs/stages/forward.md} F4, F6).
 *
 * @param maxRetryAge            A group older than this, measured from its message's creation, is
 *                               given up on at its next failure.
 * @param retryDelay             The wait after the first consecutive failure.
 * @param retryDelayGrowthFactor What each further consecutive failure multiplies the wait by.
 * @param maxRetryDelay          What the wait grows to and no further.
 */
public record ForwardBounds(Duration maxRetryAge,
                            Duration retryDelay,
                            double retryDelayGrowthFactor,
                            Duration maxRetryDelay) {

    public ForwardBounds {
        Objects.requireNonNull(maxRetryAge, "maxRetryAge");
        Objects.requireNonNull(retryDelay, "retryDelay");
        Objects.requireNonNull(maxRetryDelay, "maxRetryDelay");
        if (maxRetryAge.isNegative() || retryDelay.isNegative() || maxRetryDelay.isNegative()) {
            throw new IllegalArgumentException("Forward bounds must not be negative: " + this);
        }
        if (retryDelayGrowthFactor < 1) {
            throw new IllegalArgumentException("retryDelayGrowthFactor must be at least 1, got "
                                               + retryDelayGrowthFactor);
        }
    }

    /**
     * The longest {@link #delayAfter} can return: the flat delay when the growth factor is one,
     * the cap otherwise. A claim is held for this long at most while a wait is served, which is what
     * a queue's own claim bound has to exceed.
     */
    public Duration longestDelay() {
        return retryDelayGrowthFactor == 1
                ? retryDelay
                : maxRetryDelay.compareTo(retryDelay) > 0 ? maxRetryDelay : retryDelay;
    }

    /**
     * The wait before the next attempt after {@code consecutiveFailures} failures in a row:
     * {@code retryDelay × growth ^ (failures − 1)}, capped at {@code maxRetryDelay}.
     */
    public Duration delayAfter(final int consecutiveFailures) {
        if (consecutiveFailures < 1) {
            return Duration.ZERO;
        }
        if (retryDelayGrowthFactor == 1) {
            // Flat: the cap only means something once the delay can grow.
            return retryDelay;
        }
        final double grown = retryDelay.toMillis() * Math.pow(retryDelayGrowthFactor, consecutiveFailures - 1);
        return Duration.ofMillis((long) Math.min(grown, (double) maxRetryDelay.toMillis()));
    }
}
