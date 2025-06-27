package stroom.query.language.functions;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Objects;

public class HostAddress {
    private final InetAddress inetAddress;

    private HostAddress(final InetAddress inetAddress) {
        this.inetAddress = inetAddress;
    }

    public static HostAddress of(final String host) {
        try {
            return new HostAddress(java.net.InetAddress.getByName(host));
        } catch (final UnknownHostException e) {
            throw new IllegalArgumentException("Unknown host: " + host, e);
        }
    }

    public String getHostAddress() {
        return inetAddress.getHostAddress();
    }

    public String getHostName() {
        return inetAddress.getHostName();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof HostAddress)) {
            return false;
        }
        final HostAddress that = (HostAddress) o;
        return Objects.equals(inetAddress, that.inetAddress);
    }

    @Override
    public int hashCode() {
        return Objects.hash(inetAddress);
    }

    @Override
    public String toString() {
        return inetAddress.toString();
    }
}
