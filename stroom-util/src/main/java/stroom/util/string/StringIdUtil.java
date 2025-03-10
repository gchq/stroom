package stroom.util.string;

import stroom.util.logging.LogUtil;

public class StringIdUtil {

    /**
     * Convert id into a zero padded string that has a length that is divisible by three.
     * The length will be the shortest possible to fit the id value.
     */
    public static String idToString(final long id) {
        if (id < 0) {
            throw new IllegalArgumentException("Negative IDs not supported");
        }
        final String idStr = String.valueOf(id);
        final int remainder = idStr.length() % 3;
        return switch (remainder) {
            case 0 -> idStr; // Length is OK as is
            case 1 -> "00" + idStr;
            case 2 -> "0" + idStr;
            default -> {
                // Should never happen
                throw new IllegalStateException(
                        LogUtil.message("Unexpected remainder {}, id {}, idStr {}, len {}",
                                remainder, id, idStr, idStr.length()));
            }
        };
    }

    public static boolean isValidIdString(final String idString) {
        if (idString == null) {
            return false;
        } else {
            final int len = idString.length();
            if (len >= 3 && len % 3 == 0) {
                for (final char chr : idString.toCharArray()) {
                    if (!Character.isDigit(chr)) {
                        return false;
                    }
                }
                return true;
            } else {
                return false;
            }
        }
    }
}
