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


public interface Val extends Param, Appendable {
    Val[] EMPTY_VALUES = new Val[0];

    Integer toInteger();

    Long toLong();

    Double toDouble();

    Boolean toBoolean();

    String toString();

    Type type();

    static Val[] of(final Val... values) {
        return values;
    }

    static Val[] of(final String... str) {
        final Val[] result = new Val[str.length];
        for (int i = 0; i < result.length; i++) {
            result[i] = ValString.create(str[i]);
        }
        return Val.of(result);
    }

    static Val[] of(final double... d) {
        final Val[] result = new Val[d.length];
        for (int i = 0; i < d.length; i++) {
            result[i] = ValDouble.create(d[i]);
        }
        return Val.of(result);
    }

    static Val[] empty() {
        return EMPTY_VALUES;
    }
}
