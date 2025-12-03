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

package stroom.explorer.impl;

import stroom.explorer.shared.ExplorerNodeKey;
import stroom.util.shared.NullSafe;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class OpenItemsImpl implements OpenItems {

    private static final OpenItems ALL = new All();

    private final Set<ExplorerNodeKey> openItemSet;
    private final Set<ExplorerNodeKey> forcedOpenItemSet;

    private OpenItemsImpl(final Set<ExplorerNodeKey> openItemSet,
                          final Set<ExplorerNodeKey> forcedOpenItemSet) {
        // Ensure we have our own mutable set for openItemSet
        this.openItemSet = NullSafe.mutableSet(openItemSet);
        this.forcedOpenItemSet = NullSafe.isEmptyCollection(forcedOpenItemSet)
                ? Collections.emptySet()
                : new HashSet<>(forcedOpenItemSet);
    }

    @Override
    public boolean isOpen(final ExplorerNodeKey explorerNodeKey) {
        return openItemSet.contains(explorerNodeKey);
    }

    @Override
    public boolean isForcedOpen(final ExplorerNodeKey explorerNodeKey) {
        return forcedOpenItemSet.contains(explorerNodeKey);
    }

    @Override
    public void addAll(final Collection<ExplorerNodeKey> nodes) {
        openItemSet.addAll(nodes);
    }

    @Override
    public String toString() {
        return NullSafe.stream(openItemSet)
                .map(Objects::toString)
                .collect(Collectors.joining("\n"));
    }

    public Set<ExplorerNodeKey> getOpenItemSet() {
        return NullSafe.getOrElseGet(openItemSet, Collections::unmodifiableSet, Collections::emptySet);
    }

    public Set<ExplorerNodeKey> getForcedOpenItemSet() {
        return NullSafe.getOrElseGet(forcedOpenItemSet, Collections::unmodifiableSet, Collections::emptySet);
    }

    public static OpenItems create(final Collection<ExplorerNodeKey> openItems) {
        return new OpenItemsImpl(new HashSet<>(openItems), Collections.emptySet());
    }

    public static OpenItems createWithForced(final Collection<ExplorerNodeKey> openItems,
                                             final Collection<ExplorerNodeKey> forcedOpenItems) {
        return new OpenItemsImpl(
                NullSafe.isEmptyCollection(openItems)
                        ? Collections.emptySet()
                        : new HashSet<>(openItems),
                NullSafe.isEmptyCollection(forcedOpenItems)
                        ? Collections.emptySet()
                        : new HashSet<>(forcedOpenItems));
    }

    public static OpenItems all() {
        return ALL;
    }


    // --------------------------------------------------------------------------------


    private static class All implements OpenItems {

        @Override
        public boolean isOpen(final ExplorerNodeKey explorerNodeKey) {
            return true;
        }

        @Override
        public boolean isForcedOpen(final ExplorerNodeKey explorerNodeKey) {
            return false;
        }

        @Override
        public void addAll(final Collection<ExplorerNodeKey> nodes) {
            // Ignore as we already open all.
        }

        @Override
        public String toString() {
            return "";
        }
    }
}
