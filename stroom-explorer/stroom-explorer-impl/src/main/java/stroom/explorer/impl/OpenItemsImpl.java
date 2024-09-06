package stroom.explorer.impl;

import stroom.explorer.shared.ExplorerNodeKey;
import stroom.util.NullSafe;

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
        this.openItemSet = openItemSet;
        this.forcedOpenItemSet = forcedOpenItemSet;
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

    public static OpenItems create(final Collection<ExplorerNodeKey> openItems) {
        return new OpenItemsImpl(
                new HashSet<>(openItems),
                Collections.emptySet());
    }

    public static OpenItems createWithForced(final Collection<ExplorerNodeKey> openItems,
                                             final Collection<ExplorerNodeKey> forcedOpenItems) {
        return new OpenItemsImpl(
                new HashSet<>(openItems),
                NullSafe.isEmptyCollection(forcedOpenItems)
                        ? Collections.emptySet()
                        : new HashSet<>(forcedOpenItems));
    }

    public static OpenItems all() {
        return ALL;
    }

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
