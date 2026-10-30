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

package stroom.pathways.client.model;

import stroom.pathways.shared.otel.trace.NanoTime;
import stroom.pathways.shared.pathway.AnyBoolean;
import stroom.pathways.shared.pathway.AnyTypeValue;
import stroom.pathways.shared.pathway.BooleanValue;
import stroom.pathways.shared.pathway.ConstraintValue;
import stroom.pathways.shared.pathway.ConstraintValueType;
import stroom.pathways.shared.pathway.DoubleRange;
import stroom.pathways.shared.pathway.DoubleSet;
import stroom.pathways.shared.pathway.DoubleValue;
import stroom.pathways.shared.pathway.IntegerRange;
import stroom.pathways.shared.pathway.IntegerSet;
import stroom.pathways.shared.pathway.IntegerValue;
import stroom.pathways.shared.pathway.LongRange;
import stroom.pathways.shared.pathway.LongSet;
import stroom.pathways.shared.pathway.LongValue;
import stroom.pathways.shared.pathway.NanoTimeRange;
import stroom.pathways.shared.pathway.NanoTimeValue;
import stroom.pathways.shared.pathway.Regex;
import stroom.pathways.shared.pathway.StringSet;
import stroom.pathways.shared.pathway.StringValue;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/// A constraint's value as the text the Constraint dialog shows and reads: a set or a range is
/// comma separated (a range as `min,max`) and a duration is in nanoseconds.
public final class ConstraintValueText {

    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    private ConstraintValueText() {
        // Static methods only
    }

    /// @param value A constraint's value, or null for any value.
    /// @return The type of the value; `ANY` for null.
    public static ConstraintValueType typeOf(final ConstraintValue value) {
        return value == null
                ? ConstraintValueType.ANY
                : value.valueType();
    }

    /// @param value A constraint's value, or null for any value.
    /// @return The value as text; empty for null, and for any value or any boolean.
    public static String toText(final ConstraintValue value) {
        if (value == null) {
            return "";
        }
        return switch (value.valueType()) {
            case ANY, ANY_BOOLEAN -> "";
            case DURATION_VALUE -> String.valueOf(toNanos(((NanoTimeValue) value).getValue()));
            case DURATION_RANGE -> {
                final NanoTimeRange range = (NanoTimeRange) value;
                yield toNanos(range.getMin()) + "," + toNanos(range.getMax());
            }
            case STRING_SET -> String.join(",", ((StringSet) value).getSet());
            case INTEGER_SET -> join(((IntegerSet) value).getSet());
            case INTEGER_RANGE -> {
                final IntegerRange range = (IntegerRange) value;
                yield range.getMin() + "," + range.getMax();
            }
            case LONG_SET -> join(((LongSet) value).getSet());
            case LONG_RANGE -> {
                final LongRange range = (LongRange) value;
                yield range.getMin() + "," + range.getMax();
            }
            case DOUBLE_SET -> join(((DoubleSet) value).getSet());
            case DOUBLE_RANGE -> {
                final DoubleRange range = (DoubleRange) value;
                yield range.getMin() + "," + range.getMax();
            }
            case STRING, REGEX, BOOLEAN, INTEGER, LONG, DOUBLE -> value.toString();
        };
    }

    /// Reads a value from its text.
    ///
    /// @param type The type of value.
    /// @param text The value as text.
    /// @return The value.
    /// @throws IllegalArgumentException If the text isn't a value of the type, e.g. a number
    ///                                  that can't be read or a range without two numbers.
    public static ConstraintValue fromText(final ConstraintValueType type, final String text) {
        if (type == null) {
            throw new IllegalArgumentException("A constraint must have a type");
        }
        // Text and patterns are kept as typed; numbers and the like are read without spaces
        final String raw = text == null
                ? ""
                : text;
        final String value = raw.trim();
        return switch (type) {
            case ANY -> new AnyTypeValue();
            case DURATION_VALUE -> new NanoTimeValue(NanoTime.ofNanos(Long.parseLong(value)));
            case DURATION_RANGE -> {
                final List<String> list = range(value);
                yield new NanoTimeRange(NanoTime.ofNanos(Long.parseLong(list.get(0))),
                        NanoTime.ofNanos(Long.parseLong(list.get(1))));
            }
            case STRING -> new StringValue(raw);
            case STRING_SET -> new StringSet(Arrays.stream(raw.split(",")).collect(Collectors.toSet()));
            case REGEX -> new Regex(raw);
            case BOOLEAN -> new BooleanValue(Boolean.parseBoolean(value));
            case ANY_BOOLEAN -> new AnyBoolean();
            case INTEGER -> new IntegerValue(Integer.parseInt(value));
            case INTEGER_SET -> new IntegerSet(split(value).map(Integer::parseInt).collect(Collectors.toSet()));
            case INTEGER_RANGE -> {
                final List<String> list = range(value);
                yield new IntegerRange(Integer.parseInt(list.get(0)), Integer.parseInt(list.get(1)));
            }
            case LONG -> new LongValue(Long.parseLong(value));
            case LONG_SET -> new LongSet(split(value).map(Long::parseLong).collect(Collectors.toSet()));
            case LONG_RANGE -> {
                final List<String> list = range(value);
                yield new LongRange(Long.parseLong(list.get(0)), Long.parseLong(list.get(1)));
            }
            case DOUBLE -> new DoubleValue(Double.parseDouble(value));
            case DOUBLE_SET -> new DoubleSet(split(value).map(Double::parseDouble).collect(Collectors.toSet()));
            case DOUBLE_RANGE -> {
                final List<String> list = range(value);
                yield new DoubleRange(Double.parseDouble(list.get(0)), Double.parseDouble(list.get(1)));
            }
        };
    }

    private static long toNanos(final NanoTime nanoTime) {
        return nanoTime.getNanos() + (nanoTime.getSeconds() * NANOS_PER_SECOND);
    }

    private static String join(final Set<?> set) {
        return set.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
    }

    private static Stream<String> split(final String value) {
        return Arrays.stream(value.split(","))
                .map(String::trim);
    }

    private static List<String> range(final String value) {
        final List<String> list = split(value).collect(Collectors.toList());
        if (list.size() != 2) {
            throw new IllegalArgumentException("A range must be two values separated by a comma, e.g. 1,10");
        }
        return list;
    }
}
