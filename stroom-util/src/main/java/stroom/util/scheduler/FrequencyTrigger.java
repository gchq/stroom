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

package stroom.util.scheduler;

import stroom.util.shared.ModelStringUtil;

import java.time.Instant;

public class FrequencyTrigger implements Trigger {

    private final long interval;

    public FrequencyTrigger(final long interval) {
        this.interval = interval;
    }

    public FrequencyTrigger(final String frequency) {
        if (frequency == null || frequency.trim().length() == 0) {
            throw new NumberFormatException("Frequency expression cannot be null");
        }

        final Long duration = ModelStringUtil.parseDurationString(frequency);
        if (duration == null) {
            throw new NumberFormatException("Unable to parse frequency expression");
        }
        if (duration <= 0) {
            throw new RuntimeException("Frequency must be greater than 0");
        }

        interval = duration;
    }

    @Override
    public Instant getNextExecutionTimeAfter(final Instant afterTime) {
        return afterTime.plusMillis(interval);
    }
}
