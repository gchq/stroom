package stroom.explorer.impl;

import java.util.Optional;

interface ExplorerSession {
    Optional<Long> getMinExplorerTreeModelBuildTime();

    void setMinExplorerTreeModelBuildTime(long buildTime);
}
