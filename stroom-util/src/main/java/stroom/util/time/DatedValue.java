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

package stroom.util.time;

import stroom.util.shared.NullSafe;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Wraps a value of type T alongside a timestamp. Immutable. Allows
 * a null value.
 *
 * @param <T>
 */
public class DatedValue<T> implements Comparable<DatedValue<T>> {

    private final Instant instant;
    private final T value;

    private DatedValue(final Instant instant, final T value) {
        this.instant = Objects.requireNonNull(instant);
        this.value = value;
    }

    public static <T> DatedValue<T> create(final T value) {
        return new DatedValue<>(Instant.now(), value);
    }

    public static <T> DatedValue<T> create(final Instant instant,
                                           final T value) {
        return new DatedValue<>(instant, value);
    }

    public DatedValue<T> withNewTime(final Instant instant) {
        return new DatedValue<>(instant, this.value);
    }

    public DatedValue<T> withNewTime() {
        return new DatedValue<>(Instant.now(), this.value);
    }

    public Instant getInstant() {
        return instant;
    }

    public T getValue() {
        return value;
    }

    public boolean hasNullValue() {
        return value == null;
    }

    public Duration getAge() {
        return Duration.between(instant, Instant.now());
    }

    public boolean isOlderThan(final Duration age) {
        return instant.isBefore(Instant.now().minus(age));
    }

    @Override
    public int compareTo(final DatedValue<T> other) {
        return instant.compareTo(other.instant);
    }

    public boolean equalValue(final DatedValue<T> other) {
        return other != null && Objects.equals(value, other.value);
    }

    public static <T> boolean equalValues(final DatedValue<T> val1,
                                          final DatedValue<T> val2) {
        return NullSafe.equalProperties(val1, val2, DatedValue::getValue);
    }

    @Override
    public boolean equals(final Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || getClass() != object.getClass()) {
            return false;
        }
        final DatedValue<?> that = (DatedValue<?>) object;
        return Objects.equals(instant, that.instant) && Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(instant, value);
    }

    @Override
    public String toString() {
        return "DatedValue{" +
               "instant=" + instant +
               ", value=" + value +
               '}';
    }
}
