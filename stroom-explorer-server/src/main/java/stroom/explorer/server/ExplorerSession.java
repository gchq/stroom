package stroom.explorer.server;

import java.util.Optional;

interface ExplorerSession {
    Optional<Long> getMinExplorerTreeModelBuildTime();

    void setMinExplorerTreeModelBuildTime(long buildTime);
}
