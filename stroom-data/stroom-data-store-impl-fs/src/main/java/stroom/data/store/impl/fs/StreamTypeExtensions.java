package stroom.data.store.impl.fs;

import stroom.data.shared.StreamTypeNames;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.inject.Provider;

class StreamTypeExtensions {

    private static final Logger LOGGER = LoggerFactory.getLogger(StreamTypeExtensions.class);

    private static final Map<String, String> EXTENSION_MAP = new HashMap<>();
    private static final Map<String, String> CHILD_TYPES_REVERSE_MAP;

    static {
        EXTENSION_MAP.put(InternalStreamTypeNames.SEGMENT_INDEX, "seg");
        EXTENSION_MAP.put(InternalStreamTypeNames.BOUNDARY_INDEX, "bdy");

        // Child types
        EXTENSION_MAP.put(InternalStreamTypeNames.MANIFEST, "mf");
        EXTENSION_MAP.put(StreamTypeNames.META, "meta");
        EXTENSION_MAP.put(StreamTypeNames.CONTEXT, "ctx");

        CHILD_TYPES_REVERSE_MAP = Stream.of(
                        StreamTypeNames.META,
                        StreamTypeNames.CONTEXT,
                        InternalStreamTypeNames.MANIFEST)
                .map(type -> {
                    final String ext = EXTENSION_MAP.get(type);
                    if (ext == null) {
                        return null;
                    } else {
                        return Tuple.of(type, ext);
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(Tuple2::_2, Tuple2::_1));
    }

    private final Provider<FsVolumeConfig> fsVolumeConfigProvider;

    @Inject
    StreamTypeExtensions(final Provider<FsVolumeConfig> fsVolumeConfigProvider) {
        this.fsVolumeConfigProvider = fsVolumeConfigProvider;
    }

    String getExtension(final String streamType) {
        String extension = EXTENSION_MAP.get(streamType);
        if (extension == null) {
            extension = fsVolumeConfigProvider.get().getMetaTypeExtension(streamType)
                    .orElseGet(() -> {
                        LOGGER.debug("Unknown stream type '" + streamType + "' using extension 'dat'");
                        return "dat";
                    });
        }
        return extension;
    }

    String getChildType(final String extension) {
        return CHILD_TYPES_REVERSE_MAP.get(extension);
    }
}
