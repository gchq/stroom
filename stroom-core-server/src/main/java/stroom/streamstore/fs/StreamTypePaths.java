package stroom.streamstore.fs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class StreamTypePaths {
    private static final Logger LOGGER = LoggerFactory.getLogger(StreamTypePaths.class);
    private static final Map<String, String> PATH_MAP = new HashMap<>();

    static {
        PATH_MAP.put("Manifest", "MANIFEST");
        PATH_MAP.put("Raw Events", "RAW_EVENTS");
        PATH_MAP.put("Raw Reference", "RAW_REFERENCE");
        PATH_MAP.put("Events", "EVENTS");
        PATH_MAP.put("Reference", "REFERENCE");
        PATH_MAP.put("Test Events", "TEST_EVENTS");
        PATH_MAP.put("Test Reference", "TEST_REFERENCE");
        PATH_MAP.put("Segment Index", "SEGMENT_INDEX");
        PATH_MAP.put("Boundary Index", "BOUNDARY_INDEX");
        PATH_MAP.put("Meta Data", "META");
        PATH_MAP.put("Error", "ERROR");
        PATH_MAP.put("Context", "CONTEXT");
    }

    public static String getPath(final String streamType) {
        String path = PATH_MAP.get(streamType);
        if (path == null) {
            LOGGER.warn("Unknown stream type '" + streamType + "' using path 'OTHER'");
            path = "OTHER";
        }
        return path;
    }
}
