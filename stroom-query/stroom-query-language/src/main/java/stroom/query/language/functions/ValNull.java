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

package stroom.query.language.functions;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Comparator;

@JsonInclude(JsonInclude.Include.NON_NULL)
public final class ValNull implements Val {

    public static final ValNull INSTANCE = new ValNull();
    public static final Type TYPE = Type.NULL;

    // Only one instance of ValNull possible so all values are equal
    private static final Comparator<Val> COMPARATOR = ValComparators.asGenericComparator(
            ValNull.class,
            (val1, val2) -> 0);

    private ValNull() {
        // Use instance only
    }

    @Override
    public Integer toInteger() {
        return null;
    }

    @Override
    public Long toLong() {
        return null;
    }

    @Override
    public Float toFloat() {
        return null;
    }

    @Override
    public Double toDouble() {
        return null;
    }

    @Override
    public Boolean toBoolean() {
        return null;
    }

    @Override
    public String toString() {
        return null;
    }

    @Override
    public void appendString(final StringBuilder sb) {
        sb.append("null()");
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
        return o != null && getClass() == o.getClass();
    }

    @Override
    public int hashCode() {
        return 0;
    }

    @Override
    public Comparator<Val> getDefaultComparator(final boolean isCaseSensitive) {
        return COMPARATOR;
    }

    @Override
    public Object unwrap() {
        return null;
    }
}
