package stroom.explorer.impl;

import stroom.explorer.shared.ExplorerNodeKey;
import stroom.util.NullSafe;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class OpenItemsImpl implements OpenItems {

    private static final OpenItems NONE = new None();
    private static final OpenItems ALL = new All();

    private final Set<ExplorerNodeKey> openItemSet;

    private OpenItemsImpl(final Set<ExplorerNodeKey> openItemSet) {
        this.openItemSet = openItemSet;
    }

    @Override
    public boolean isOpen(final ExplorerNodeKey explorerNodeKey) {
        return openItemSet.contains(explorerNodeKey);
    }

    @Override
    public String toString() {
        return NullSafe.stream(openItemSet)
                .map(Objects::toString)
                .collect(Collectors.joining("\n"));
    }

    public static OpenItems create(final Collection<ExplorerNodeKey> openItems) {
        if (openItems == null || openItems.isEmpty()) {
            return NONE;
        }
        return new OpenItemsImpl(new HashSet<>(openItems));
    }

    public static OpenItems none() {
        return NONE;
    }

    public static OpenItems all() {
        return ALL;
    }

    private static class None implements OpenItems {

        @Override
        public boolean isOpen(final ExplorerNodeKey explorerNodeKey) {
            return false;
        }

        @Override
        public String toString() {
            return "";
        }
    }

    private static class All implements OpenItems {

        @Override
        public boolean isOpen(final ExplorerNodeKey explorerNodeKey) {
            return true;
        }

        @Override
        public String toString() {
            return "";
        }
    }
}
