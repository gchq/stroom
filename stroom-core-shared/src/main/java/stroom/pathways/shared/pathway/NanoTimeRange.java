/*
 * Copyright 2025 Crown Copyright
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

package stroom.pathways.shared.pathway;

import stroom.pathways.shared.otel.trace.NanoTime;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * How long a span took, as the widest and narrowest seen so far.
 *
 * <p>Either end may be absent, which means nothing is asserted on that side: a duration with no
 * bottom is any duration up to the top, and one with no top is any duration from the bottom up. An
 * end goes missing because the pathway document names it — {@code duration.min} or
 * {@code duration.max} — for a span whose timing swings so widely that learning that end fills the
 * model with outliers and ends up asserting nothing anyway.
 */
@JsonInclude(Include.NON_NULL)
public final class NanoTimeRange extends AbstractRange<NanoTime> implements ConstraintValue {

    @JsonCreator
    public NanoTimeRange(@JsonProperty("min") final NanoTime min,
                         @JsonProperty("max") final NanoTime max) {
        super(min, max);
    }

    @Override
    public ConstraintValueType valueType() {
        if (getMin() == null) {
            return ConstraintValueType.DURATION_AT_MOST;
        }
        if (getMax() == null) {
            return ConstraintValueType.DURATION_AT_LEAST;
        }
        return ConstraintValueType.DURATION_RANGE;
    }

    @Override
    public String toString() {
        return end(getMin()) + " -> " + end(getMax());
    }

    private static String end(final NanoTime bound) {
        return bound == null
                ? "any"
                : bound.toString();
    }
}
