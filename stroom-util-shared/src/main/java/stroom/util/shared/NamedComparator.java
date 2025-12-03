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

package stroom.util.shared;

import java.util.Comparator;
import java.util.Objects;

/**
 * A comparator that you can name.  This is useful for debugging/logging if you are dealing with
 * lots of different comparators.
 */
public class NamedComparator<T> implements Comparator<T> {

    private final String name;
    private final Comparator<T> comparator;

    public NamedComparator(final String name, final Comparator<T> comparator) {
        this.name = Objects.requireNonNull(name);
        this.comparator = Objects.requireNonNull(comparator);
    }

    public static <T> NamedComparator<T> create(final String name, final Comparator<T> comparator) {
        return new NamedComparator<>(name, comparator);
    }

    @Override
    public int compare(final T o1, final T o2) {
        return comparator.compare(o1, o2);
    }

    @Override
    public String toString() {
        return "NamedComparator{" +
                "name='" + name + '\'' +
                ", comparator=" + comparator +
                '}';
    }
}
