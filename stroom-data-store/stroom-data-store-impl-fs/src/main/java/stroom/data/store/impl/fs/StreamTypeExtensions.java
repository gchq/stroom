package stroom.data.store.impl.fs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

class StreamTypeExtensions {
    private static final Logger LOGGER = LoggerFactory.getLogger(StreamTypeExtensions.class);
    private static final Map<String, String> EXTENSION_MAP = new HashMap<>();

    static {
        EXTENSION_MAP.put("Manifest", "mf");
        EXTENSION_MAP.put("Raw Events", "revt");
        EXTENSION_MAP.put("Raw Reference", "rref");
        EXTENSION_MAP.put("Events", "evt");
        EXTENSION_MAP.put("Reference", "ref");
        EXTENSION_MAP.put("Test Events", "tevt");
        EXTENSION_MAP.put("Test Reference", "tref");
        EXTENSION_MAP.put("Segment Index", "seg");
        EXTENSION_MAP.put("Boundary Index", "bdy");
        EXTENSION_MAP.put("Meta Data", "meta");
        EXTENSION_MAP.put("Error", "err");
        EXTENSION_MAP.put("Context", "ctx");
    }

    static String getExtension(final String streamType) {
        String extension = EXTENSION_MAP.get(streamType);
        if (extension == null) {
            LOGGER.warn("Unknown stream type '" + streamType + "' using extension 'dat'");
            extension = "dat";
        }
        return extension;
    }
}
