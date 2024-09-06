package stroom.explorer.impl;

import stroom.explorer.shared.ExplorerNodeKey;

import java.util.Collection;

public interface OpenItems {

    boolean isOpen(ExplorerNodeKey explorerNodeKey);

    boolean isForcedOpen(ExplorerNodeKey explorerNodeKey);

    void addAll(Collection<ExplorerNodeKey> nodes);
}
