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


import stroom.util.shared.GwtNullSafe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

@JsonPropertyOrder({"add", "remove"})
@JsonInclude(Include.NON_NULL)
public class Changes {

    @JsonProperty
    // A map of user/group UUID => Set of permission names to add
    private final Map<String, Set<String>> add;
    @JsonProperty
    // A map of user/group UUID => Set of permission names to remove
    private final Map<String, Set<String>> remove;

    @JsonCreator
    public Changes(@JsonProperty("add") final Map<String, Set<String>> add,
                   @JsonProperty("remove") final Map<String, Set<String>> remove) {
        this.add = add;
        this.remove = remove;
    }

    /**
     * @return A map of user/group UUID => Set of permission names
     */
    public Map<String, Set<String>> getAdd() {
        return add;
    }

    /**
     * @return A map of user/group UUID => Set of permission names
     */
    public Map<String, Set<String>> getRemove() {
        return remove;
    }

    @JsonIgnore
    public boolean hasChanges() {
        if (add == null && remove == null) {
            return false;
        } else {
            return Stream.of(
                    GwtNullSafe.map(add),
                    GwtNullSafe.map(remove))
                    .mapToLong(map -> map.values().size())
                    .sum() > 0;
        }
    }

    @Override
    public String toString() {
        return "Additions:\n"
                + DocumentPermissions.permsMapToStr(add)
                + "\nRemovals:\n"
                + DocumentPermissions.permsMapToStr(remove);
    }
}
