package stroom.event.logging.api;

import java.util.Objects;

public class ObjectType {
    private final Class<?> objectClass;

    public ObjectType(final Class<?> objectClass) {
        this.objectClass = objectClass;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final ObjectType objectType = (ObjectType) o;
        return Objects.equals(objectClass, objectType.objectClass);
    }

    @Override
    public int hashCode() {
        return Objects.hash(objectClass);
    }

    @Override
    public String toString() {
        return "ObjectClass{" +
                "objectClass=" + objectClass +
                '}';
    }
}
