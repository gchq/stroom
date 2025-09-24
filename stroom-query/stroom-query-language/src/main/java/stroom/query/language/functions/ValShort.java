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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Comparator;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public final class ValShort implements ValNumber {

    private static final Comparator<Val> COMPARATOR = ValComparators.asGenericComparator(
            ValShort.class, ValComparators.AS_INTEGER_COMPARATOR);

    public static final Type TYPE = Type.SHORT;

    @JsonProperty
    private final short value;

    @JsonCreator
    private ValShort(@JsonProperty("value") final short value) {
        this.value = value;
    }

    public static ValShort create(final short value) {
        final int offset = 128;
        if (value >= -128 && value <= 127) { // will cache
            return ValShortCache.cache[value + offset];
        }
        return new ValShort(value);
    }

    public short getValue() {
        return value;
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
        return (float) value;
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
        return String.valueOf(value);
    }

    @Override
    public Number toNumber() {
        return value;
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
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ValShort that = (ValShort) o;
        return value == that.value;
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


    // --------------------------------------------------------------------------------


    private static class ValShortCache {

        static final ValShort[] cache = new ValShort[-(-128) + 127 + 1];

        static {
            for (int i = 0; i < cache.length; i++) {
                cache[i] = new ValShort((short) (i - 128));
            }
        }

        private ValShortCache() {
        }
    }
}
