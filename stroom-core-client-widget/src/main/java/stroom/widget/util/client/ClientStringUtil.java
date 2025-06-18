package stroom.widget.util.client;

public class ClientStringUtil {
    public static String zeroPad(final int amount, final int value) {
        return zeroPad(amount, "" + value);
    }

    public static String zeroPad(final int amount, final String in) {
        final int left = amount - in.length();
        final StringBuilder out = new StringBuilder();
        for (int i = 0; i < left; i++) {
            out.append("0");
        }
        out.append(in);
        return out.toString();
    }

    public static int getInt(final String string) {
        int index = -1;
        final char[] chars = string.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            if (chars[i] == '0') {
                index = i;
            } else {
                break;
            }
        }
        String trimmed = string;
        if (index != -1) {
            trimmed = trimmed.substring(index + 1);
        }
        if (trimmed.length() == 0) {
            return 0;
        }
        return Integer.parseInt(trimmed);
    }
}
