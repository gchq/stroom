package stroom.proxy.app.event;

import stroom.util.date.DateUtil;

public class EventStoreFile {

    public static final String DOT = ".";
    public static final String TIME_DELIMITER = "=";
    public static final String LOG_EXTENSION = ".log";
    public static final String TEMP_EXTENSION = ".temp";

    public static String createTempFileName(final String prefix) {
        return prefix +
                TEMP_EXTENSION;
    }

    public static String createRolledFileName(final String prefix) {
        return prefix +
                TIME_DELIMITER +
                DateUtil.createFileDateTimeString() +
                LOG_EXTENSION;
    }

    public static boolean isTempFile(final String fileName) {
        return fileName.endsWith(TEMP_EXTENSION);
    }

    public static String getPrefix(final String fileName) {
        String prefix = fileName;
        int index = fileName.lastIndexOf(DOT);
        if (index != -1) {
            prefix = fileName.substring(0, index);
        }

        index = fileName.lastIndexOf(TIME_DELIMITER);
        if (index != -1) {
            // Make sure the delimiter we have found isn't the feed key delimiter.
            final int keyIndex = fileName.indexOf(FeedKey.DELIMITER);
            if (keyIndex != index) {
                prefix = fileName.substring(0, index);
            }
        }

        return prefix;
    }
}
