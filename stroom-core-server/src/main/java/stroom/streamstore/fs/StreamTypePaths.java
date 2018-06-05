package stroom.streamstore.fs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class StreamTypePaths {
    private static final Logger LOGGER = LoggerFactory.getLogger(StreamTypePaths.class);
    private static final Map<String, String> PATH_MAP = new HashMap<>();
    private static final Map<String, String> TYPE_MAP = new HashMap<>();

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
        TYPE_MAP.put(path, name);
    }

    public static String getPath(final String streamType) {
        String path = PATH_MAP.get(streamType);
        if (path == null) {
            LOGGER.warn("Unknown stream type '" + streamType + "' using path 'OTHER'");
            path = "OTHER";
        }
        return path;
    }

    public static String getType(final String path) {
        String type = TYPE_MAP.get(path);
        if (type == null) {
            LOGGER.error("Unknown stream type for path '"  + path + "'");
        }
        return type;
    }
}
