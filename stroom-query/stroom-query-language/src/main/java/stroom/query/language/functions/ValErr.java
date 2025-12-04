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

import stroom.util.shared.NullSafe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Comparator;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public final class ValErr implements Val {

    public static final String PREFIX = "ERR: ";
    public static final Comparator<Val> CASE_SENSITIVE_COMPARATOR = ValComparators.asGenericComparator(
            ValErr.class,
            ValComparators.AS_DOUBLE_THEN_CASE_SENSITIVE_STRING_COMPARATOR,
            ValComparators.GENERIC_CASE_SENSITIVE_COMPARATOR);
    public static final Comparator<Val> CASE_INSENSITIVE_COMPARATOR = ValComparators.asGenericComparator(
            ValErr.class,
            ValComparators.AS_DOUBLE_THEN_CASE_INSENSITIVE_STRING_COMPARATOR,
            ValComparators.GENERIC_CASE_INSENSITIVE_COMPARATOR);

    public static final ValErr INSTANCE = new ValErr("Unknown");
    public static final Type TYPE = Type.ERR;
    @JsonProperty
    private final String message;

    @JsonCreator
    private ValErr(@JsonProperty("message") final String message) {
        this.message = message;
    }

    public static ValErr create(final String message) {
        if (NullSafe.isBlankString(message)) {
            return INSTANCE;
        } else {
            return new ValErr(message);
        }
    }

    public static ValErr create(final Throwable throwable) {
        return NullSafe.getOrElse(
                throwable,
                Throwable::getMessage,
                ValErr::create,
                INSTANCE);
    }

    public static Val wrap(final Val val) {
        if (val.type().isError()) {
            return val;
        } else {
            return ValErr.INSTANCE;
        }
    }

    public static Val wrap(final Val val, final String message) {
        if (val.type().isError()) {
            return val;
        } else {
            return create(message);
        }
    }

    public static Val wrap(final Val val, final ValErr alternative) {
        if (val.type().isError()) {
            return val;
        } else {
            return alternative;
        }
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
        return PREFIX + message;
    }

    @Override
    public void appendString(final StringBuilder sb) {
        sb.append("err()");
    }

    @Override
    public Type type() {
        return TYPE;
    }

    String getMessage() {
        return message;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ValErr valErr = (ValErr) o;
        return Objects.equals(message, valErr.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(message);
    }

    @Override
    public Comparator<Val> getDefaultComparator(final boolean isCaseSensitive) {
        return isCaseSensitive
                ? CASE_SENSITIVE_COMPARATOR
                : CASE_INSENSITIVE_COMPARATOR;
    }

    @Override
    public Object unwrap() {
        return toString();
    }
}
