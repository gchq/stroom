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

package stroom.util;

import stroom.util.io.ByteSize;
import stroom.util.logging.DurationTimer;
import stroom.util.shared.NullSafe;
import stroom.util.time.StroomDuration;

import java.time.Duration;

/**
 * Util methods for safely working with things that might be null.
 * <p>
 * MUST contain only methods that cannot go in {@link NullSafe} due
 * to GWT compilation constraints.
 * </p>
 *
 */
public class NullSafeExtra {

    private NullSafeExtra() {
    }

    /**
     * Returns the passed stroomDuration if it is non-null else returns a ZERO {@link StroomDuration}
     */
    public static StroomDuration duration(final StroomDuration stroomDuration) {
        return stroomDuration != null
                ? stroomDuration
                : StroomDuration.ZERO;
    }

    /**
     * Returns the passed duration if it is non-null else returns a ZERO {@link Duration}
     */
    public static Duration duration(final Duration duration) {
        return duration != null
                ? duration
                : Duration.ZERO;
    }

    /**
     * Returns the passed durationTimer if it is non-null else returns a ZERO {@link DurationTimer}
     */
    public static DurationTimer durationTimer(final DurationTimer durationTimer) {
        return durationTimer != null
                ? durationTimer
                : DurationTimer.ZERO;
    }

    /**
     * Returns the passed byteSize if it is non-null else returns a ZERO {@link ByteSize}
     */
    public static ByteSize byteSize(final ByteSize byteSize) {
        return byteSize != null
                ? byteSize
                : ByteSize.ZERO;
    }
}
