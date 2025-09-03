package stroom.planb.impl.serde.trace;

import java.util.HexFormat;

public class HexStringUtil {

    private static final byte[] EMPTY = new byte[0];

    public static String encode(final byte[] byteArray) {
        if (byteArray == null || byteArray.length == 0) {
            return "";
        }
        return HexFormat.of().formatHex(byteArray);
    }

    public static byte[] decode(final String hexString) {
        if (hexString == null || hexString.isEmpty()) {
            return EMPTY;
        }
        return HexFormat.of().parseHex(hexString);
    }
}
