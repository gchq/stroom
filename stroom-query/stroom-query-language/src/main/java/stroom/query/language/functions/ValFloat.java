/*
 * Copyright 2018 Crown Copyright
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

package stroom.query.language.functions;

import stroom.util.concurrent.LazyBoolean;
import stroom.util.concurrent.LazyValue;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.math.DoubleMath;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public final class ValFloat implements ValNumber {

    private static final Comparator<Val> COMPARATOR = ValComparators.asGenericComparator(
            ValFloat.class, ValComparators.AS_FLOAT_COMPARATOR);

    public static final Type TYPE = Type.FLOAT;
    @JsonProperty
    private final float value;
    @JsonIgnore
    private final transient LazyValue<String> lazyStringValue;
    @JsonIgnore
    private final transient LazyBoolean lazyHasFractionalPart;

    @JsonCreator
    private ValFloat(@JsonProperty("value") final float value) {
        this.value = value;
        this.lazyStringValue = LazyValue.initialisedBy(this::deriveStringValue);
        this.lazyHasFractionalPart = LazyBoolean.initialisedBy(this::deriveHasFractionalPart);
    }

    public static ValFloat create(final float value) {
        return new ValFloat(value);
    }

    @Override
    public Integer toInteger() {
        return (int) value;
    }

    @Override
    public Long toLong() {
        return (long) value;
    }

    @Override
    public Float toFloat() {
        return value;
    }

    @Override
    public Double toDouble() {
        return (double) value;
    }

    @Override
    public Boolean toBoolean() {
        return value != 0;
    }

    @Override
    public String toString() {
        return lazyStringValue.getValueWithoutLocks();
    }

    @Override
    public Number toNumber() {
        return hasFractionalPart()
                ? value
                : toLong();
    }

    private String deriveStringValue() {
        String stringValue = null;
        try {
            final BigDecimal bigDecimal = BigDecimal.valueOf(value);
            stringValue = bigDecimal.stripTrailingZeros().toPlainString();
        } catch (final RuntimeException e) {
            // Can't be represented as a string
        }
        return stringValue;
    }

    @Override
    public void appendString(final StringBuilder sb) {
        sb.append(this);
    }

    @Override
    public Type type() {
        return TYPE;
    }

    @Override
    public boolean hasFractionalPart() {
        return lazyHasFractionalPart.getValueWithoutLocks();
    }

    private boolean deriveHasFractionalPart() {
        return !DoubleMath.isMathematicalInteger(value);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ValFloat valFloat = (ValFloat) o;
        return Float.compare(valFloat.value, value) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public Comparator<Val> getDefaultComparator(final boolean isCaseSensitive) {
        return COMPARATOR;
    }

    @Override
    public Object unwrap() {
        return value;
    }
}
