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

import io.vavr.Tuple;
import io.vavr.Tuple4;

import java.util.ArrayList;
import java.util.Collection;

public class QuadList<T1, T2, T3, T4> extends ArrayList<Tuple4<T1, T2, T3, T4>> {

    public QuadList(final int initialCapacity) {
        super(initialCapacity);
    }

    public QuadList() {
        super();
    }

    public QuadList(final Collection<? extends Tuple4<T1, T2, T3, T4>> c) {
        super(c);
    }

    public void add(final T1 value1, final T2 value2, final T3 value3, final T4 value4) {
        super.add(Tuple.of(value1, value2, value3, value4));
    }
}
