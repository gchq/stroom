package stroom.data.store.impl.fs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

class MockFsTypePaths implements FsTypePathDao {

    private static final Logger LOGGER = LoggerFactory.getLogger(MockFsTypePaths.class);
    private static final Map<String, String> PATH_MAP = new HashMap<>();

    static {
        put("Manifest", "MANIFEST");
        put("Raw Events", "RAW_EVENTS");
        put("Raw Reference", "RAW_REFERENCE");
        put("Events", "EVENTS");
        put("Reference", "REFERENCE");
        put("Test Events", "TEST_EVENTS");
        put("Test Reference", "TEST_REFERENCE");
        put("Segment Index", "SEGMENT_INDEX");
        put("Boundary Index", "BOUNDARY_INDEX");
        put("Meta Data", "META");
        put("Error", "ERROR");
        put("Context", "CONTEXT");
    }

    private static void put(final String name, final String path) {
        PATH_MAP.put(name, path);
    }

    @Override
    public String getOrCreatePath(final String streamType) {
        String path = PATH_MAP.get(streamType);
        if (path == null) {
            path = streamType.toUpperCase().replaceAll("\\W", "_");
            LOGGER.warn("Non standard stream type '" + streamType + "' using path '" + path + "'");
            PATH_MAP.put(streamType, path);
        }
        return path;
    }
}
