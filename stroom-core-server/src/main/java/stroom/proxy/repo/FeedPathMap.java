package stroom.proxy.repo;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class FeedPathMap {
    private final boolean completedAllFiles;
    private final Map<String, List<Path>> map;

    public FeedPathMap(final boolean completedAllFiles, final Map<String, List<Path>> map) {
        this.completedAllFiles = completedAllFiles;
        this.map = map;
    }

    public boolean isCompletedAllFiles() {
        return completedAllFiles;
    }

    public Map<String, List<Path>> getMap() {
        return map;
    }
}
