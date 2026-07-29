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

package stroom.pipeline.shared.data;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.ToIntFunction;

@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder({"add, remove"})
public abstract class AbstractAddRemove<T> {

    @JsonProperty
    protected final List<T> add;
    @JsonProperty
    protected final List<T> remove;

    @JsonCreator
    public AbstractAddRemove(@JsonProperty("add") final List<T> add,
                             @JsonProperty("remove") final List<T> remove) {
        this.add = add;
        this.remove = remove;
    }

    public List<T> getAdd() {
        return add;
    }

    public List<T> getRemove() {
        return remove;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        @SuppressWarnings("unchecked") // Same class, so same type parameter.
        final AbstractAddRemove<T> that = (AbstractAddRemove<T>) o;
        return listsEqual(add, that.add) &&
               listsEqual(remove, that.remove);
    }

    @Override
    public int hashCode() {
        return listHashCode(add) * 31 + listHashCode(remove);
    }

    /**
     * Compares an add or remove list with the equivalent list from another instance. Order is
     * significant by default; subclasses whose list order carries no meaning should override this
     * (and {@link #listHashCode(List)}) so that equality reflects content alone.
     */
    protected boolean listsEqual(final List<T> list, final List<T> other) {
        return Objects.equals(list, other);
    }

    /**
     * @see #listsEqual(List, List)
     */
    protected int listHashCode(final List<T> list) {
        return Objects.hashCode(list);
    }

    /**
     * Compares two lists ignoring order, using the natural ordering of the items to pair them up and
     * the supplied equality test to compare each pair. Used by subclasses whose list order is not
     * meaningful, so that a list rebuilt in a different order (e.g. by
     * {@code PipelineModel.diff()}, which iterates hash based maps) still compares equal.
     */
    static <T extends Comparable<T>> boolean unorderedEquals(final List<T> list,
                                                             final List<T> other,
                                                             final BiPredicate<T, T> equality) {
        if (list == other) {
            return true;
        }
        if (list == null || other == null || list.size() != other.size()) {
            return false;
        }

        final List<T> sorted = new ArrayList<>(list);
        final List<T> otherSorted = new ArrayList<>(other);
        Collections.sort(sorted);
        Collections.sort(otherSorted);

        for (int i = 0; i < sorted.size(); i++) {
            if (!equality.test(sorted.get(i), otherSorted.get(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * @see #unorderedEquals(List, List, BiPredicate)
     */
    static <T> int unorderedHashCode(final List<T> list, final ToIntFunction<T> hash) {
        if (list == null) {
            return 0;
        }
        int result = list.size();
        for (final T item : list) {
            // Sum so that the hash does not depend on order.
            result += hash.applyAsInt(item);
        }
        return result;
    }

    @Override
    public String toString() {
        return "add=" + add +
               ", remove=" + remove;
    }
}
