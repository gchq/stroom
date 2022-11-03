package stroom.data.store.impl.fs;

import stroom.data.shared.StreamTypeNames;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

class MockFsTypePaths implements FsTypePathDao {

    private static final Logger LOGGER = LoggerFactory.getLogger(MockFsTypePaths.class);
    private static final Map<String, String> PATH_MAP = new HashMap<>();

    static {
        put("Manifest", "MANIFEST");
        put(StreamTypeNames.RAW_EVENTS, "RAW_EVENTS");
        put(StreamTypeNames.RAW_REFERENCE, "RAW_REFERENCE");
        put(StreamTypeNames.EVENTS, "EVENTS");
        put(StreamTypeNames.REFERENCE, "REFERENCE");
        put(StreamTypeNames.TEST_EVENTS, "TEST_EVENTS");
        put(StreamTypeNames.TEST_REFERENCE, "TEST_REFERENCE");
        put(StreamTypeNames.META, "META");
        put(StreamTypeNames.ERROR, "ERROR");
        put(StreamTypeNames.CONTEXT, "CONTEXT");
        put(InternalStreamTypeNames.SEGMENT_INDEX, "SEGMENT_INDEX");
        put(InternalStreamTypeNames.BOUNDARY_INDEX, "BOUNDARY_INDEX");
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
