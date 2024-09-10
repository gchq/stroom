package stroom.docstore.api;

import java.io.Serializable;
import java.util.Objects;

public class DocumentTypeName implements Serializable {

    private final String name;

    public DocumentTypeName(final String name) {
        this.name = name;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DocumentTypeName entityType = (DocumentTypeName) o;
        return Objects.equals(name, entityType.name);
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
