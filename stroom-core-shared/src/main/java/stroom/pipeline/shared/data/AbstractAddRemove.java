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

import java.util.List;
import java.util.Objects;

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
        final AbstractAddRemove<?> that = (AbstractAddRemove<?>) o;
        return Objects.equals(add, that.add) &&
               Objects.equals(remove, that.remove);
    }

    @Override
    public int hashCode() {
        return Objects.hash(add, remove);
    }
}
