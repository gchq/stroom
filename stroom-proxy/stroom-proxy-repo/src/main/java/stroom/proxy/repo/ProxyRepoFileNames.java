package stroom.proxy.repo;

import stroom.util.io.FileNameUtil;

public class ProxyRepoFileNames {

    public static final String META_EXTENSION = ".meta";
    public static final String ZIP_EXTENSION = ".zip";
    public static final String ERROR_EXTENSION = ".err";
    public static final String BAD_EXTENSION = ".bad";

    public static String getMeta(final String name) {
        return getBase(name) + META_EXTENSION;
    }

    public static String getZip(final String name) {
        return getBase(name) + ZIP_EXTENSION;
    }

    public static String getError(final String name) {
        return getBase(name) + ERROR_EXTENSION;
    }

    public static String getBad(final String name) {
        if (!name.endsWith(ZIP_EXTENSION)) {
            throw new RuntimeException("Unexpected extension");
        }
        return name + BAD_EXTENSION;
    }

    public static String getBase(final String name) {
        if (name.endsWith(ZIP_EXTENSION)) {
            return FileNameUtil.getBaseName(name);
        }
        return name;
    }
}
