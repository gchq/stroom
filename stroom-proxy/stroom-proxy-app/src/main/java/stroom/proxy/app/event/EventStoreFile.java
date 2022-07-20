package stroom.proxy.app.event;

import stroom.util.date.DateUtil;

import java.nio.file.Path;
import java.time.Instant;

public class EventStoreFile {

    public static final String TIME_DELIMITER = "=";
    public static final String LOG_EXTENSION = ".log";

    public static FeedKey getFeedKey(final Path path) {
        final String fileName = path.getFileName().toString();

        final int timeIndex = fileName.lastIndexOf(TIME_DELIMITER);
        if (timeIndex == -1) {
            throw new RuntimeException("No time delimiter found");
        }

        // Make sure the delimiter we have found isn't the feed key delimiter.
        final int keyIndex = fileName.indexOf(FeedKey.DELIMITER);
        if (keyIndex == timeIndex) {
            throw new RuntimeException("Unexpected index");
        }

        final String prefix = fileName.substring(0, timeIndex);
        return FeedKey.decodeKey(prefix);
    }

    public static Path createNew(final Path dir, final FeedKey feedKey, final Instant instant) {
        return dir.resolve(feedKey.encodeKey() +
                TIME_DELIMITER +
                DateUtil.createFileDateTimeString(instant.toEpochMilli()) +
                LOG_EXTENSION);
    }
}
