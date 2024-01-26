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

import stroom.util.concurrent.LazyValue;

import java.util.Comparator;
import java.util.Objects;

public final class ValDate implements ValNumber {

    public static Comparator<Val> COMPARATOR = ValComparators.asGenericComparator(
            ValDate.class, ValComparators.AS_LONG_COMPARATOR);

    public static final Type TYPE = Type.DATE;
    private final long value;
    private transient final LazyValue<String> lazyStringValue;

    private ValDate(final long value) {
        this.value = value;
        this.lazyStringValue = LazyValue.initialisedBy(this::deriveStringValue);
    }

    public static ValDate create(final long value) {
        return new ValDate(value);
    }

    public static ValDate create(final String date) {
        return new ValDate(DateUtil.parseNormalDateTimeString(date));
    }

    @Override
    public Integer toInteger() {
        return (int) value;
    }

    @Override
    public Long toLong() {
        return value;
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
        return lazyStringValue.getValueWithoutLocks();
    }

    private String deriveStringValue() {
        try {
            return DateUtil.createNormalDateTimeString(value);
        } catch (final RuntimeException e) {
            return null;
        }
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
        final ValDate valDate = (ValDate) o;
        return value == valDate.value;
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public Comparator<Val> getDefaultComparator() {
        return COMPARATOR;
    }
}
