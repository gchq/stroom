package stroom.data.store.impl.fs;

import stroom.data.shared.StreamTypeNames;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

class StreamTypeExtensions {
    private static final Logger LOGGER = LoggerFactory.getLogger(StreamTypeExtensions.class);
    private static final Map<String, String> EXTENSION_MAP = new HashMap<>();
    private static final Map<String, String> EXTENSION_REVERSE_MAP;

    static {
        EXTENSION_MAP.put(InternalStreamTypeNames.MANIFEST, "mf");
        EXTENSION_MAP.put(InternalStreamTypeNames.SEGMENT_INDEX, "seg");
        EXTENSION_MAP.put(InternalStreamTypeNames.BOUNDARY_INDEX, "bdy");
        EXTENSION_MAP.put(StreamTypeNames.RAW_EVENTS, "revt");
        EXTENSION_MAP.put(StreamTypeNames.RAW_REFERENCE, "rref");
        EXTENSION_MAP.put(StreamTypeNames.EVENTS, "evt");
        EXTENSION_MAP.put(StreamTypeNames.REFERENCE, "ref");
        EXTENSION_MAP.put(StreamTypeNames.TEST_EVENTS, "tevt");
        EXTENSION_MAP.put(StreamTypeNames.TEST_REFERENCE, "tref");
        EXTENSION_MAP.put(StreamTypeNames.META, "meta");
        EXTENSION_MAP.put(StreamTypeNames.ERROR, "err");
        EXTENSION_MAP.put(StreamTypeNames.CONTEXT, "ctx");
        EXTENSION_MAP.put(StreamTypeNames.DETECTIONS, "dtxn");
        EXTENSION_MAP.put(StreamTypeNames.RECORDS, "rec");

        EXTENSION_REVERSE_MAP = EXTENSION_MAP.entrySet()
                .stream()
                .collect(Collectors.toMap(Entry::getValue, Entry::getKey));
    }

    static String getExtension(final String streamType) {
        String extension = EXTENSION_MAP.get(streamType);
        if (extension == null) {
            LOGGER.warn("Unknown stream type '" + streamType + "' using extension 'dat'");
            extension = "dat";
        }
        return extension;
    }

    static String getType(final String extension) {
        return EXTENSION_REVERSE_MAP.get(extension);
    }
}
