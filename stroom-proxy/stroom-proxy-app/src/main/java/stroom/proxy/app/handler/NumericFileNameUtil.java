package stroom.proxy.app.handler;

import com.google.common.base.Strings;

public class NumericFileNameUtil {

    /**
     * Create a string to use as part of a file name that is a `0` padded number.
     *
     * @param num The number to create the name from.
     * @return A `0` padded string representing the supplied number.
     */
    public static String create(final long num) {
        return Strings.padStart(Long.toString(num), 10, '0');
    }
}
