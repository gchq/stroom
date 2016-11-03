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

package stroom.entity.shared;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import stroom.util.shared.SharedObject;

public class FetchSet implements SharedObject {
    private static final long serialVersionUID = -3867859869578102437L;

    private boolean fetchAll;
    private Set<FetchDirective> fetchDirectives;

    public FetchSet() {
        // Default constructor necessary for GWT serialisation.
    }

    public FetchSet(final boolean fetchAll) {
        this.fetchAll = fetchAll;
    }

    public FetchSet(final FetchDirective... directives) {
        if (directives != null && directives.length > 0) {
            fetchDirectives = new HashSet<FetchDirective>(Arrays.asList(directives));
        }
    }

    public void add(final FetchDirective fetchDirective) {
        if (fetchDirectives == null) {
            fetchDirectives = new HashSet<FetchDirective>();
        }
        fetchDirectives.add(fetchDirective);
    }

    public void remove(final FetchDirective fetchDirective) {
        if (fetchDirectives != null) {
            fetchDirectives.remove(fetchDirective);
            if (fetchDirectives.size() == 0) {
                fetchDirectives = null;
            }
        }
    }

    public void clear() {
        fetchDirectives = null;
    }

    public void addAll(final FetchSet fetchSet) {
        if (fetchSet.fetchDirectives != null && fetchSet.fetchDirectives.size() > 0) {
            for (final FetchDirective fetchDirective : fetchSet.fetchDirectives) {
                add(fetchDirective);
            }
        }
    }

    public boolean fetch(final FetchDirective fetchDirective) {
        if (fetchAll) {
            return true;
        }
        if (fetchDirectives == null) {
            return false;
        }
        return fetchDirectives.contains(fetchDirective);
    }
}
