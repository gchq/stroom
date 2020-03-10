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

package stroom.security.shared;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.HashSet;
import java.util.Set;

@JsonPropertyOrder({"addSet", "removeSet"})
@JsonInclude(Include.NON_NULL)
public class ChangeSet<T> {
    @JsonProperty
    private final Set<T> addSet;
    @JsonProperty
    private final Set<T> removeSet;

    public ChangeSet() {
        addSet = new HashSet<>();
        removeSet = new HashSet<>();
    }

    @JsonCreator
    public ChangeSet(@JsonProperty("addSet") final Set<T> addSet,
                     @JsonProperty("removeSet") final Set<T> removeSet) {
        this.addSet = addSet;
        this.removeSet = removeSet;
    }

    public void add(final T item) {
        if (!removeSet.remove(item)) {
            addSet.add(item);
        }
    }

    public void remove(final T item) {
        if (!addSet.remove(item)) {
            removeSet.add(item);
        }
    }

    public Set<T> getAddSet() {
        return addSet;
    }

    public Set<T> getRemoveSet() {
        return removeSet;
    }
}
