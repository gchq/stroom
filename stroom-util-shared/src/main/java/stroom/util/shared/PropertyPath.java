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
public class PropertyPath implements Comparable<PropertyPath> {

    private static final String DELIMITER = ".";
    private static final String DELIMITER_REGEX = "\\" + DELIMITER;

    private static final PropertyPath EMPTY_INSTANCE = new PropertyPath(Collections.emptyList());

    // Held in part form to reduce memory overhead as some parts will be used
    // many times over all the config objects
    private final List<String> parts;

    public static PropertyPath blank() {
        return EMPTY_INSTANCE;
    }

    /**
     * Create a {@link PropertyPath} from a path string, e.g "stroom.node.name"
     */
    public static PropertyPath fromPathString(final String propertyPath) {
        return PropertyPath.fromParts(propertyPath.split(DELIMITER_REGEX));
    }

    /**
     * Create a {@link PropertyPath} from a path string, e.g ["stroom", "node", "name"]
     */
    public static PropertyPath fromParts(final String... parts) {
        return new PropertyPath(Arrays.asList(parts));
    }

    /**
     * Create a {@link PropertyPath} from a path string, e.g ["stroom", "node", "name"]
     */
    public static PropertyPath fromParts(final List<String> parts) {
        if (parts.isEmpty()) {
            return EMPTY_INSTANCE;
        } else {
            return new PropertyPath(parts);
        }
    }

    private PropertyPath(final List<String> parts) {
        this.parts = new ArrayList<>(parts);
    }

    /**
     * Merge otherPath onto the end of this and return a new {@link PropertyPath}
     */
    public PropertyPath merge(final PropertyPath otherPath) {
        List<String> mergedParts = new ArrayList<>(this.parts);
        mergedParts.addAll(otherPath.parts);
        return new PropertyPath(mergedParts);
    }

    /**
     * Merge part onto the end of this and return a new {@link PropertyPath}
     */
    public PropertyPath merge(final String part) {
        List<String> mergedParts = new ArrayList<>(this.parts);
        mergedParts.add(part);
        return new PropertyPath(mergedParts);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public int compareTo(final PropertyPath other) {
        return toString().compareTo(other.toString());
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

    public boolean equalsIgnoreCase(final PropertyPath o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (parts.size() != o.parts.size()) {
            return false;
        }
        for (int i = 0; i < parts.size(); i++) {
            if (!parts.get(i).equalsIgnoreCase(o.parts.get(i))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(parts);
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
}
