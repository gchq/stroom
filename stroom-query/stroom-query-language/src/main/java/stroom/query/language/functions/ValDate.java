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

import java.time.Instant;
import java.util.Comparator;
import java.util.Objects;

public final class ValDate implements ValNumber {

    private static final Comparator<Val> COMPARATOR = ValComparators.asGenericComparator(
            ValDate.class, ValComparators.AS_LONG_COMPARATOR);

    public static final Type TYPE = Type.DATE;
    private final long epochMs;
    private final transient LazyValue<String> lazyStringValue;

    private ValDate(final long epochMs) {
        this.epochMs = epochMs;
        this.lazyStringValue = LazyValue.initialisedBy(this::deriveStringValue);
    }

    public static ValDate create(final long value) {
        return new ValDate(value);
    }

    public static ValDate create(final String date) {
        return new ValDate(DateUtil.parseNormalDateTimeString(date));
    }

    public static ValDate create(final Instant instant) {
        return new ValDate(Objects.requireNonNull(instant).toEpochMilli());
    }

    @Override
    public Integer toInteger() {
        return (int) epochMs;
    }

    @Override
    public Long toLong() {
        return epochMs;
    }

    @Override
    public Float toFloat() {
        return (float) epochMs;
    }

    @Override
    public Double toDouble() {
        return (double) epochMs;
    }

    @Override
    public Boolean toBoolean() {
        return epochMs != 0;
    }

    @Override
    public String toString() {
        return lazyStringValue.getValueWithoutLocks();
    }

    @Override
    public Number toNumber() {
        return epochMs;
    }

    private String deriveStringValue() {
        try {
            return DateUtil.createNormalDateTimeString(epochMs);
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
        return epochMs == valDate.epochMs;
    }

    @Override
    public int hashCode() {
        return Objects.hash(epochMs);
    }

    @Override
    public Comparator<Val> getDefaultComparator(final boolean isCaseSensitive) {
        return COMPARATOR;
    }
}
