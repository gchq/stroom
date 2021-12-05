package stroom.explorer.impl;

import java.util.Optional;

interface ExplorerSession {

    Optional<Long> getMinExplorerTreeModelId();

    void setMinExplorerTreeModelId(long id);
}
