package stroom.util.net;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class IpAddressUtil {

    private IpAddressUtil() {
    }

    public static InetAddress ipAddressFromString(final String address) throws UnknownHostException {
        return InetAddress.getByName(address);
    }

    /**
     * Converts a string-based IPv4 address to its numeric representation
     * @param address An IPv4 address, such as `192.168.1.0`
     * @return A numeric representation of the IP address
     */
    public static long toNumericIpAddress(final String address) throws UnknownHostException {
        final byte[] addressBytes = ipAddressFromString(address).getAddress();
        long value = 0;
        for (final byte b : addressBytes) {
            value = (value << 8) | (b & 0xFF);
        }
        return value;
    }
}
