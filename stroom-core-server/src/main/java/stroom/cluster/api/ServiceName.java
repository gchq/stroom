package stroom.cluster.api;

import java.io.Serializable;
import java.util.Objects;

public class ServiceName implements Serializable {
    private final String name;

    public ServiceName(final String name) {
        this.name = name;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final ServiceName serviceName = (ServiceName) o;
        return Objects.equals(name, serviceName.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public String toString() {
        return name;
    }
}
