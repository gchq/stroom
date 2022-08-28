package stroom.util.string;

import java.util.Arrays;

public class StringIdUtil {

    public static String idToString(final long id) {
        final String string = String.valueOf(id);
        if (string.length() % 3 == 0) {
            return string;
        }

        final char[] chars = string.toCharArray();
        final int len = (int) Math.ceil(chars.length / 3D) * 3;
        final char[] arr = new char[len];
        final int pos = len - chars.length;
        Arrays.fill(arr, 0, pos, '0');
        System.arraycopy(chars, 0, arr, pos, chars.length);
        return String.valueOf(arr);
    }
}
