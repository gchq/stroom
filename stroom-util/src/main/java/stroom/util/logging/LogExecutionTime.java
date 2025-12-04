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

package stroom.util.logging;

import stroom.util.shared.ModelStringUtil;

import java.time.Duration;
import java.time.Instant;

/**
 * Class to output timings.
 */
public class LogExecutionTime {

    private final Instant startTime = Instant.now();

    public static LogExecutionTime start() {
        return new LogExecutionTime();
    }

    public long getDurationMs() {
        return getDuration().toMillis();
    }

    public Duration getDuration() {
        return Duration.between(startTime, Instant.now());
    }

    @Override
    public String toString() {
        return ModelStringUtil.formatDurationString(getDurationMs());
    }
}
