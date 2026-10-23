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

package stroom.pathways.impl;

import stroom.pathways.shared.otel.trace.NanoTime;
import stroom.pathways.shared.pathway.AnyBoolean;
import stroom.pathways.shared.pathway.AnyTypeValue;
import stroom.pathways.shared.pathway.BooleanValue;
import stroom.pathways.shared.pathway.ConstraintValue;
import stroom.pathways.shared.pathway.DoubleRange;
import stroom.pathways.shared.pathway.DoubleValue;
import stroom.pathways.shared.pathway.IntegerRange;
import stroom.pathways.shared.pathway.IntegerValue;
import stroom.pathways.shared.pathway.LongRange;
import stroom.pathways.shared.pathway.LongValue;
import stroom.pathways.shared.pathway.NanoTimeRange;
import stroom.pathways.shared.pathway.NanoTimeValue;
import stroom.pathways.shared.pathway.StringSet;
import stroom.pathways.shared.pathway.StringValue;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * What a constraint was given, summarised as it arrives.
 *
 * <p>The same shapes the model holds — a range for things that order, a set for things that do not —
 * so a summary serialises with the code that writes a constraint and reads back the same way. What it
 * does not do is any of the model's judgement: nothing generalises to a regex, nothing changes type,
 * nothing becomes optional. This is a plain account of what arrived, and the model's own rules are
 * applied to it when the two meet.
 */
final class ObservedValues {

    /**
     * How many distinct values are kept for something that does not order. Past this the summary says
     * only that there were too many to list, which is enough for the one question asked of it: whether
     * the values seen lately are narrower than the ones the model holds.
     */
    static final int MAX_VALUES = 20;

    private ObservedValues() {
    }

    /**
     * Two accounts of what was given, combined into one that covers both. Either side may be a single
     * value or an account already covering several, so a trace folds into a day with the same call a
     * window's worth of days fold into each other.
     */
    static ConstraintValue add(final ConstraintValue seen, final ConstraintValue observed) {
        if (observed == null) {
            return seen;
        }
        if (seen == null) {
            return observed;
        }
        if (seen.equals(observed)) {
            return seen;
        }
        if (seen instanceof NanoTimeValue || seen instanceof NanoTimeRange) {
            return times(seen, observed);
        }
        if (seen instanceof IntegerValue || seen instanceof IntegerRange) {
            return integers(seen, observed);
        }
        if (seen instanceof LongValue || seen instanceof LongRange) {
            return longs(seen, observed);
        }
        if (seen instanceof DoubleValue || seen instanceof DoubleRange) {
            return doubles(seen, observed);
        }
        if (seen instanceof StringValue || seen instanceof StringSet) {
            return strings(seen, observed);
        }
        if (seen instanceof BooleanValue || seen instanceof AnyBoolean) {
            // Two different booleans is both of them, and there are only two. Anything else is a type
            // change, which this gives up on the same way round as every other.
            return observed instanceof BooleanValue
                    ? new AnyBoolean()
                    : new AnyTypeValue();
        }
        // Already says there was too much to list, or is a type this does not summarise. Either way
        // one more account tells it nothing it does not already admit.
        return seen;
    }

    private static ConstraintValue times(final ConstraintValue a, final ConstraintValue b) {
        final NanoTime low = lowTime(a);
        final NanoTime high = highTime(a);
        final NanoTime otherLow = lowTime(b);
        final NanoTime otherHigh = highTime(b);
        if (low == null || high == null || otherLow == null || otherHigh == null) {
            return new AnyTypeValue();
        }
        return new NanoTimeRange(
                low.isGreaterThan(otherLow)
                        ? otherLow
                        : low,
                high.isLessThan(otherHigh)
                        ? otherHigh
                        : high);
    }

    private static NanoTime lowTime(final ConstraintValue value) {
        return switch (value) {
            case final NanoTimeValue single -> single.getValue();
            case final NanoTimeRange range -> range.getMin();
            case null, default -> null;
        };
    }

    private static NanoTime highTime(final ConstraintValue value) {
        return switch (value) {
            case final NanoTimeValue single -> single.getValue();
            case final NanoTimeRange range -> range.getMax();
            case null, default -> null;
        };
    }

    private static ConstraintValue integers(final ConstraintValue a, final ConstraintValue b) {
        final Integer low = lowInteger(a);
        final Integer high = highInteger(a);
        final Integer otherLow = lowInteger(b);
        final Integer otherHigh = highInteger(b);
        return low == null || otherLow == null
                ? new AnyTypeValue()
                : new IntegerRange(Math.min(low, otherLow), Math.max(high, otherHigh));
    }

    private static Integer lowInteger(final ConstraintValue value) {
        return switch (value) {
            case final IntegerValue single -> single.getValue();
            case final IntegerRange range -> range.getMin();
            case null, default -> null;
        };
    }

    private static Integer highInteger(final ConstraintValue value) {
        return switch (value) {
            case final IntegerValue single -> single.getValue();
            case final IntegerRange range -> range.getMax();
            case null, default -> null;
        };
    }

    private static ConstraintValue longs(final ConstraintValue a, final ConstraintValue b) {
        final Long low = lowLong(a);
        final Long high = highLong(a);
        final Long otherLow = lowLong(b);
        final Long otherHigh = highLong(b);
        return low == null || otherLow == null
                ? new AnyTypeValue()
                : new LongRange(Math.min(low, otherLow), Math.max(high, otherHigh));
    }

    private static Long lowLong(final ConstraintValue value) {
        return switch (value) {
            case final LongValue single -> single.getValue();
            case final LongRange range -> range.getMin();
            case null, default -> null;
        };
    }

    private static Long highLong(final ConstraintValue value) {
        return switch (value) {
            case final LongValue single -> single.getValue();
            case final LongRange range -> range.getMax();
            case null, default -> null;
        };
    }

    private static ConstraintValue doubles(final ConstraintValue a, final ConstraintValue b) {
        final Double low = lowDouble(a);
        final Double high = highDouble(a);
        final Double otherLow = lowDouble(b);
        final Double otherHigh = highDouble(b);
        return low == null || otherLow == null
                ? new AnyTypeValue()
                : new DoubleRange(Math.min(low, otherLow), Math.max(high, otherHigh));
    }

    private static Double lowDouble(final ConstraintValue value) {
        return switch (value) {
            case final DoubleValue single -> single.getValue();
            case final DoubleRange range -> range.getMin();
            case null, default -> null;
        };
    }

    private static Double highDouble(final ConstraintValue value) {
        return switch (value) {
            case final DoubleValue single -> single.getValue();
            case final DoubleRange range -> range.getMax();
            case null, default -> null;
        };
    }

    private static ConstraintValue strings(final ConstraintValue a, final ConstraintValue b) {
        final Set<String> values = new LinkedHashSet<>(stringsOf(a));
        final Set<String> other = stringsOf(b);
        if (values.isEmpty() || other.isEmpty()) {
            return new AnyTypeValue();
        }
        values.addAll(other);
        return values.size() > MAX_VALUES
                ? new AnyTypeValue()
                : new StringSet(values);
    }

    private static Set<String> stringsOf(final ConstraintValue value) {
        return switch (value) {
            case final StringValue single -> Set.of(single.getValue());
            case final StringSet set -> set.getSet();
            case null, default -> Set.of();
        };
    }
}
