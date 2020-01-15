package stroom.util.shared;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Class for representing a path to a property in an object tree, i.e
 * stroom.node.name
 */
public class PropertyPath {

    private static final String DELIMITER = ".";
    private static final String DELIMITER_REGEX = "\\" + DELIMITER;

    private static final PropertyPath EMPTY_INSTANCE = new PropertyPath(Collections.emptyList());

    // Held in part form to reduce memory overhead as some parts will be used
    // many times over all the config objects
    private final List<String> parts;

    public static PropertyPath blank() {
        return EMPTY_INSTANCE;
    }

    public static PropertyPath from(final String propertyPath) {
        return PropertyPath.from(propertyPath.split(DELIMITER_REGEX));
    }

    public static PropertyPath from(final String... parts) {
        return new PropertyPath(Arrays.asList(parts));
    }

    public static PropertyPath from(final List<String> parts) {
        if (parts.isEmpty()) {
            return EMPTY_INSTANCE;
        } else {
            return new PropertyPath(parts);
        }
    }

    private PropertyPath(final List<String> parts) {
        this.parts = new ArrayList<>(parts);
    }

    public PropertyPath merge(final PropertyPath otherPath) {
        List<String> mergedParts = new ArrayList<>(this.parts);
        mergedParts.addAll(otherPath.parts);
        return new PropertyPath(mergedParts);
    }

    public PropertyPath merge(final String part) {
        List<String> mergedParts = new ArrayList<>(this.parts);
        mergedParts.add(part);
        return new PropertyPath(mergedParts);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private List<String> parts = null;

        public Builder add(final String part) {
            if (parts == null) {
                parts = new ArrayList<>();
            }
            parts.add(part);
            return this;
        }

        public PropertyPath build() {
            return new PropertyPath(parts);
        }
    }

    @Override
    public String toString() {
        return String.join(DELIMITER, parts);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final PropertyPath that = (PropertyPath) o;
        return parts.equals(that.parts);
    }

    @Override
    public int hashCode() {
        return Objects.hash(parts);
    }
}
