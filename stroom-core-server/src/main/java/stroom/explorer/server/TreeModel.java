package stroom.explorer.server;

import stroom.explorer.shared.ExplorerData;

import java.util.List;
import java.util.Map;

public interface TreeModel {
    void add(ExplorerData parent, ExplorerData child);

    Map<ExplorerData, List<ExplorerData>> getChildMap();
}
