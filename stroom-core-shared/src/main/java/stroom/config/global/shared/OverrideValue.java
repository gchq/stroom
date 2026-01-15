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

package stroom.config.global.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

@JsonInclude(Include.NON_NULL)
public class OverrideValue<T> {

    private static final OverrideValue<?> UNSET = new OverrideValue<>(false, null);
    private static final OverrideValue<?> NULL_VALUE = new OverrideValue<>(true, null);

    @JsonProperty
    private final boolean hasOverride;
    @JsonProperty
    private final T value;

    @SuppressWarnings("unchecked")
    public static <T> OverrideValue<T> unSet(final Class<T> type) {
        return (OverrideValue<T>) UNSET;
    }

    @SuppressWarnings("unchecked")
    public static <T> OverrideValue<T> withNullValue(final Class<T> type) {
        return (OverrideValue<T>) NULL_VALUE;
    }

    @SuppressWarnings("unchecked")
    public static <T> OverrideValue<T> with(final T value) {
        if (value == null) {
            return (OverrideValue<T>) NULL_VALUE;
        } else {
            return new OverrideValue<>(true, value);
        }
    }

    // pkg private so GWT can see it
    @JsonCreator
    OverrideValue(@JsonProperty("hasOverride") final boolean hasOverride,
                  @JsonProperty("value") final T value) {
        this.hasOverride = hasOverride;
        this.value = value;
    }

    // Horrible name, but can't seem to get RestyGWT to use @JsonGetter
    public boolean isHasOverride() {
        return hasOverride;
    }

    public T getValue() {
        return value;
    }

    /**
     * @return The override value if present or empty if the override has been explicitly set to
     * null/empty. If there is no override then a {@link RuntimeException} will be thrown.
     * Use {@link OverrideValue#isHasOverride()} to check if there is an override value.
     */
    @JsonIgnore
    public Optional<T> getValueAsOptional() {
        if (!hasOverride) {
            throw new RuntimeException("No override present");
        }
        return Optional.ofNullable(value);
    }

    /**
     * Return the non-null override value or other if the override has explicitly been set to null/empty
     */
    public T getValueOrElse(final T other) {
        if (!hasOverride) {
            throw new RuntimeException("No override present");
        }
        if (value != null) {
            return value;
        } else {
            return other;
        }
    }

    public T getValueOrElse(final T valueIfUnSet, final T valueIfNull) {
        if (!hasOverride) {
            return valueIfUnSet;
        } else if (value == null) {
            return valueIfNull;
        } else {
            return value;
        }
    }

    /**
     * If an override value is present then the passed consumer will consume the override value
     * optional which may be empty if a null/empty override has explicitly been set.
     */
    public void ifOverridePresent(final Consumer<Optional<T>> consumer) {
        if (hasOverride) {
            consumer.accept(Optional.ofNullable(value));
        }
    }

    @Override
    public String toString() {
        return "Override{" +
                "hasOverride=" + hasOverride +
                ", value=" + value +
                '}';
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final OverrideValue<?> overrideValue = (OverrideValue<?>) o;
        return hasOverride == overrideValue.hasOverride &&
                Objects.equals(value, overrideValue.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(hasOverride, value);
    }
}
