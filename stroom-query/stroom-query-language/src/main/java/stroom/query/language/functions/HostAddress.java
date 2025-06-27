package stroom.query.language.functions;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Objects;

public class HostAddress {
    private final InetAddress InetAddress;

    private HostAddress(final InetAddress inetAddress) {
        this.InetAddress = inetAddress;
    }

    public static HostAddress of(final String host) {
        try {
            return new HostAddress(java.net.InetAddress.getByName(host));
        } catch (final UnknownHostException e) {
            throw new IllegalArgumentException("Unknown host: " + host, e);
        }
    }

    public String getHostAddress() {
        return InetAddress.getHostAddress();
    }

    public String getHostName() {
        return InetAddress.getHostName();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o){
            return true;
        }
        if (!(o instanceof HostAddress)){
            return false;
        }
        final HostAddress that = (HostAddress) o;
        return Objects.equals(InetAddress, that.InetAddress);
    }

    @Override
    public int hashCode() {
        return Objects.hash(InetAddress);
    }

    @Override
    public String toString() {
        return InetAddress.toString();
    }
}
