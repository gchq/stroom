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

import stroom.util.shared.SharedObject;

import java.util.HashSet;
import java.util.Set;

public class ChangeSet<T> implements SharedObject {
    private static final long serialVersionUID = -1740543177783532223L;

    private Set<T> addSet = new HashSet<T>();
    private Set<T> removeSet = new HashSet<T>();

    public ChangeSet() {
        // Default constructor necessary for GWT serialisation.
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

    public void setAddSet(final Set<T> addSet) {
        this.addSet = addSet;
    }

    public Set<T> getRemoveSet() {
        return removeSet;
    }

    public void setRemoveSet(final Set<T> removeSet) {
        this.removeSet = removeSet;
    }
}
