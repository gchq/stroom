package stroom.config.global.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;


import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

public class OverrideValue<T> {
    private static final OverrideValue UNSET =  new OverrideValue<>(false, null);
    private static final OverrideValue NULL_VALUE =  new OverrideValue<>(true, null);

    private boolean hasOverride;
    @JsonProperty("value")
    private T value;

    @SuppressWarnings("unused")
    public OverrideValue() {
        // Required for GWT serialisation
    }

    public boolean isHasOverride() {
        return hasOverride;
    }

    public void setHasOverride(final boolean hasOverride) {
        this.hasOverride = hasOverride;
    }

    public T getValue() {
        return value;
    }

    public void setValue(final T value) {
        this.value = value;
    }

    @JsonIgnore
    @SuppressWarnings("unchecked")
    public static <T> OverrideValue<T> unSet() {
        return (OverrideValue<T>) UNSET;
    }

    @JsonIgnore
    @SuppressWarnings("unchecked")
    public static <T> OverrideValue<T> withNullValue() {
        return (OverrideValue<T>) NULL_VALUE;
    }

    @JsonIgnore
    @SuppressWarnings("unchecked")
    public static <T> OverrideValue<T> with(final T value) {
        if (value == null) {
            return (OverrideValue<T>) NULL_VALUE;
        } else {
            return new OverrideValue<>(true, value);
        }
    }

    @JsonCreator
    private OverrideValue(final @JsonProperty("hasOverride") boolean hasOverride,
                          final @JsonProperty("value") T value) {
        this.hasOverride = hasOverride;
        this.value = value;
    }

    @JsonProperty("hasOverride")
    public boolean hasOverride() {
        return hasOverride;
    }

    /**
     * @return The override value if present or empty if the override has been explicitly set to
     * null/empty. If there is no override then a {@link RuntimeException} will be thrown.
     * Use {@link OverrideValue#hasOverride()} to check if there is an override value.
     */
    @JsonIgnore
    public Optional<T> getVal() {
        if (!hasOverride) {
            throw new RuntimeException("No override present");
        }
        return Optional.ofNullable(value);
    }

    /**
     * Return the non-null override value or other if the override has explicitly been set to null/empty
     */
    @JsonIgnore
    public T getValOrElse(final T other) {
        if (!hasOverride) {
            throw new RuntimeException("No override present");
        }
        if (value != null) {
            return value;
        } else {
            return other;
        }
    }

    @JsonIgnore
    public T getValOrElse(final T valueIfUnSet, final T valueIfNull) {
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
    @JsonIgnore
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
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final OverrideValue<?> overrideValue = (OverrideValue<?>) o;
        return hasOverride == overrideValue.hasOverride &&
                Objects.equals(value, overrideValue.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(hasOverride, value);
    }
}
