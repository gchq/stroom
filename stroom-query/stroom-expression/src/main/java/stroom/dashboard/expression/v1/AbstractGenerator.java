/*
 * Copyright 2016 Crown Copyright
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

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

abstract class AbstractGenerator implements Generator, Comparable<Generator> {

    private static final AutoComparator COMPARATOR = new AutoComparator();

    @Override
    public final int compareTo(final Generator gen) {
        final Val o1 = eval(null);
        final Val o2 = gen.eval(null);
        return COMPARATOR.compare(o1, o2);
    }

    @Override
    public void read(final Input input) {
    }

    @Override
    public void write(final Output output) {
    }
}
