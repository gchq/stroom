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

import java.util.Objects;

public final class ValDuration implements Val {

    public static final Type TYPE = Type.DURATION;
    private final SimpleDuration value;

    private ValDuration(final SimpleDuration value) {
        this.value = value;
    }

    public static ValDuration create(final SimpleDuration value) {
        return new ValDuration(value);
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

    public SimpleDuration toDuration() {
        return value;
    }

    @Override
    public String toString() {
        return value.toString();
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
        final ValDuration valDuration = (ValDuration) o;
        return value == valDuration.value;
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }
}
