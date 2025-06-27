package stroom.query.language.functions;

import java.util.Objects;

public class HostName {
    private final String hostName;

    private HostName(final String hostName) {
        this.hostName = Objects.requireNonNull(hostName, "hostName can't be null");
    }

    public static HostName of(final String hostName) {
        return new HostName(hostName);
    }

    public String get() {
        return hostName;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof HostName)) {
            return false;
        }
        final HostName that = (HostName) o;
        return Objects.equals(hostName, that.hostName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(hostName);
    }

    @Override
    public String toString() {
        return hostName;
    }
}
