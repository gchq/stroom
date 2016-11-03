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

package stroom.dashboard.expression;

import java.io.Serializable;

public abstract class AbstractGenerator implements Generator, Serializable, Comparable<Object> {
    private static final long serialVersionUID = 513621715143449935L;

    @Override
    public int compareTo(final Object o) {
        final Generator gen = (Generator) o;
        final Object o1 = eval();
        final Object o2 = gen.eval();

        return ObjectCompareUtil.compare(o1, o2);
    }
}
