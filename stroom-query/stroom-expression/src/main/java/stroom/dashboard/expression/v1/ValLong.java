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

package stroom.dashboard.expression.v1;

import java.util.Objects;

public class ValLong implements ValNumber {
    private static final Type TYPE = new LongType();
    private long value;

    ValLong() {
    }

    ValLong(final long value) {
        this.value = value;
    }

    public static ValLong create(final long value) {
        final int offset = 128;
        if (value >= -128 && value <= 127) { // will cache
            return ValLongCache.cache[(int) value + offset];
        }
        return new ValLong(value);
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
    public void appendString(final StringBuilder sb) {
        sb.append(toString());
    }

    @Override
    public Type type() {
        return TYPE;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final ValLong valLong = (ValLong) o;
        return value == valLong.value;
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public int compareTo(final Val o) {
        return Long.compare(value, ((ValLong) o).value);
    }

    private static class ValLongCache {
        static final ValLong cache[] = new ValLong[-(-128) + 127 + 1];

        static {
            for (int i = 0; i < cache.length; i++)
                cache[i] = new ValLong(i - 128);
        }

        private ValLongCache() {
        }
    }

    private static class LongType implements Type {
        private static final String NAME = "long";

        @Override
        public boolean isValue() {
            return true;
        }

        @Override
        public boolean isNumber() {
            return true;
        }

        @Override
        public boolean isError() {
            return false;
        }

        @Override
        public boolean isNull() {
            return false;
        }

        @Override
        public String toString() {
            return NAME;
        }
    }
}
