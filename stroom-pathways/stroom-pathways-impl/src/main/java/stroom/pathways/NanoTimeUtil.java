package stroom.pathways;

import java.time.Instant;

public class NanoTimeUtil {

    public static Instant get(final String time) {
        if (time == null) {
            return null;
        }
        if (time.length() > 9) {
            final String nanoString = time.substring(time.length() - 9);
            final String secondString = time.substring(0, time.length() - 9);
            return Instant.ofEpochSecond(Long.parseLong(secondString)).plusNanos(Long.parseLong(nanoString));
        } else {
            return Instant.ofEpochSecond(0).plusNanos(Long.parseLong(time));
        }
    }
}
