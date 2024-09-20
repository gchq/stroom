package stroom.aws.s3.impl;

import stroom.data.shared.StreamTypeNames;

import java.util.HashMap;
import java.util.Map;

public class S3FileExtensions {
    public static final String MANIFEST_FILE_NAME = "001.mf";
    public static final String ZIP_FILE_NAME = "temp.zip";
    public static final String DATA_EXTENSION = ".dat";
    public static final String ZIP_EXTENSION = ".zip";
    public static final String INDEX_EXTENSION = ".idx";
    public static final String META_EXTENSION = ".meta";
    public static final String CONTEXT_EXTENSION = ".ctx";
    public static final Map<String, String> EXTENSION_MAP = new HashMap<>();

    static {
        EXTENSION_MAP.put(StreamTypeNames.META, META_EXTENSION);
        EXTENSION_MAP.put(StreamTypeNames.CONTEXT, CONTEXT_EXTENSION);
    }

}
