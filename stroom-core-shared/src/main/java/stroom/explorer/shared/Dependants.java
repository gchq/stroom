/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.explorer.shared;

import stroom.docref.DocRef;
import stroom.util.shared.NullSafe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;

/**
 * The documents that depend on a set of documents that are about to be deleted.
 * <p>
 * {@link #visibleDependants} are the dependant documents the current user has VIEW permission on, so
 * their identity can safely be shown. {@link #hasHiddenDependants} is {@code true} when there is at
 * least one further dependant the user is not permitted to view; its existence is disclosed but its
 * identity is not.
 */
@JsonInclude(Include.NON_NULL)
public class Dependants {

    public static final Dependants EMPTY = new Dependants(Collections.emptyList(), false);

    @JsonProperty
    private final List<DocRef> visibleDependants;
    @JsonProperty
    private final boolean hasHiddenDependants;

    @JsonCreator
    public Dependants(@JsonProperty("visibleDependants") final List<DocRef> visibleDependants,
                      @JsonProperty("hasHiddenDependants") final boolean hasHiddenDependants) {
        this.visibleDependants = visibleDependants;
        this.hasHiddenDependants = hasHiddenDependants;
    }

    public List<DocRef> getVisibleDependants() {
        return visibleDependants;
    }

    public boolean isHasHiddenDependants() {
        return hasHiddenDependants;
    }

    @JsonIgnore
    public boolean isEmpty() {
        return !hasHiddenDependants && !NullSafe.hasItems(visibleDependants);
    }
}
