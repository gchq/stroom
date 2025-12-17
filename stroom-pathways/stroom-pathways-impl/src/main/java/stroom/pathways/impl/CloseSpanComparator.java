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

package stroom.pathways.impl;

import stroom.pathways.shared.otel.trace.NanoDuration;
import stroom.pathways.shared.otel.trace.NanoTime;
import stroom.pathways.shared.otel.trace.Span;
import stroom.util.shared.time.SimpleDuration;

import java.util.Comparator;

public class CloseSpanComparator implements Comparator<Span> {

    private final NanoDuration tolerance;

    public CloseSpanComparator(final SimpleDuration simpleDuration) {
        if (simpleDuration == null) {
            tolerance = NanoDuration.ZERO;
        } else {
            this.tolerance = switch (simpleDuration.getTimeUnit()) {
                case NANOSECONDS -> NanoDuration.ofNanos(simpleDuration.getTime());
                case MILLISECONDS -> NanoDuration.ofMillis(simpleDuration.getTime());
                case SECONDS -> NanoDuration.ofSeconds(simpleDuration.getTime());
                case MINUTES -> NanoDuration.ofSeconds(simpleDuration.getTime() * 60);
                case HOURS -> NanoDuration.ofSeconds(simpleDuration.getTime() * 60 * 60);
                default -> throw new RuntimeException("Unable to convert simple duration");
            };
        }
    }

    public CloseSpanComparator(final NanoDuration tolerance) {
        this.tolerance = tolerance;
    }

    @Override
    public int compare(final Span o1, final Span o2) {
        final NanoTime start1 = NanoTime.fromString(o1.getStartTimeUnixNano());
        final NanoTime start2 = NanoTime.fromString(o2.getStartTimeUnixNano());
        final NanoDuration diff = start1.diff(start2);
        // If there is less duration than the supplied tolerance between then sort by name.
        if (diff.isLessThanEquals(tolerance)) {
            return o1.getName().compareTo(o2.getName());
        }
        return start1.compareTo(start2);
    }
}
