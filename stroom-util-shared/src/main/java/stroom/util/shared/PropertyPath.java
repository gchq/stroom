package stroom.util.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import stroom.docref.SharedObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Class for representing a path to a property in an object tree, i.e
 * stroom.node.name
 */
public class PropertyPath implements SharedObject, Comparable<PropertyPath> {

    private static final long serialVersionUID = 4298017138182350850L;

    private static final String DELIMITER = ".";
    private static final String DELIMITER_REGEX = "\\" + DELIMITER;

    private static final PropertyPath EMPTY_INSTANCE = new PropertyPath(Collections.emptyList());

    // Held in part form to reduce memory overhead as some parts will be used
    // many times over all the config objects
    @JsonProperty("parts")
    private List<String> parts;

    @SuppressWarnings("unused")
    PropertyPath() {
        // Pkg private for GWT
    }

    @JsonCreator
    PropertyPath(@JsonProperty("parts") final List<String> parts) {
        if (parts == null) {
            this.parts = Collections.emptyList();
        } else {
            this.parts = new ArrayList<>(parts);
        }
    }

    public static PropertyPath blank() {
        return EMPTY_INSTANCE;
    }

    /**
     * Create a {@link PropertyPath} from a path string, e.g "stroom.node.name"
     */
    public static PropertyPath fromPathString(final String propertyPath) {
        if (propertyPath == null || propertyPath.isEmpty()) {
            return EMPTY_INSTANCE;
        } else {
            return PropertyPath.fromParts(propertyPath.split(DELIMITER_REGEX));
        }
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

    /**
     * @return The property name from a property path, i.e. "name" from "stroom.node.name"
     */
    @JsonIgnore
    public String getPropertyName() {
        if (parts.isEmpty()) {
            throw new RuntimeException("Unable to get property name from empty path");
        }
        return parts.get(parts.size() - 1);
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
        if (parts == null || parts.isEmpty()) {
            return null;
        } else {
            return String.join(DELIMITER, parts);
        }
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

    public static class Builder implements SharedObject {

        private static final long serialVersionUID = 4646431868659034045L;

        private List<String> parts = null;

        @SuppressWarnings("unused") // Needed for GWT
        private Builder() {
        }

        /**
         * Add path part to the end of the list of paths already added
         */
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
