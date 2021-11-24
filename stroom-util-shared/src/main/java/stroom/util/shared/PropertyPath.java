/*
 * Copyright 2021 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.util.shared;

import stroom.docref.HasName;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Class for representing a path to a property in an object tree, i.e
 * stroom.node.name
 * The aim is to break the dot delimited path strings into its parts to
 * reduce the memory overhead of holding all the paths as many parts are similar
 */
@JsonInclude(Include.NON_NULL)
public class PropertyPath implements Comparable<PropertyPath>, HasName {

    private static final String DELIMITER = ".";
    private static final String DELIMITER_REGEX = "\\" + DELIMITER;

    private static final PropertyPath EMPTY_INSTANCE = new PropertyPath(Collections.emptyList());

    // Held in part form to reduce memory overhead as some parts will be used
    // many times over all the config objects
    @JsonProperty("parts")
    private final List<String> parts;

    @JsonCreator
    PropertyPath(@JsonProperty("parts") final List<String> parts) {
        if (parts == null) {
            this.parts = Collections.emptyList();
        } else {
            validateParts(parts);
            this.parts = new ArrayList<>(parts);
        }
    }

    private PropertyPath(final List<String> parts1, final List<String> parts2) {
        parts = new ArrayList<>(parts1.size() + parts2.size());
        parts.addAll(parts1);
        parts.addAll(parts2);
        validateParts(parts);
    }

    private PropertyPath(final List<String> parts1, final String finalPart) {
        parts = new ArrayList<>(parts1.size() + 1);
        parts.addAll(parts1);
        parts.add(finalPart);
        validateParts(parts);
    }

    public static PropertyPath blank() {
        return EMPTY_INSTANCE;
    }

    @JsonIgnore
    public boolean isBlank() {
        return parts.isEmpty();
    }

    public boolean containsPart(final String part) {
        return parts.contains(part);
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
            validateParts(parts);
            return new PropertyPath(parts);
        }
    }

    private static void validateParts(final List<String> parts) {
        for (final String part : parts) {
            if (part == null || part.isEmpty()) {
                throw new RuntimeException("Null or empty path part in " + parts);
            }
        }
    }

    List<String> getParts() {
        if (parts.isEmpty()) {
            return Collections.emptyList();
        } else {
            return new ArrayList<>(parts);
        }
    }

    /**
     * Merge otherPath onto the end of this and return a new {@link PropertyPath}
     */
    public PropertyPath merge(final PropertyPath otherPath) {
        if (otherPath == null || otherPath.isBlank()) {
            return this;
        } else {
            return new PropertyPath(this.parts, otherPath.parts);
        }
    }

    /**
     * Merge part onto the end of this and return a new {@link PropertyPath}
     */
    public PropertyPath merge(final String part) {
        if (part == null || part.isEmpty()) {
            return this;
        } else {
            return new PropertyPath(this.parts, part);
        }
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

    @Override
    public int compareTo(final PropertyPath other) {
        return toString().compareTo(other.toString());
    }

    @Override
    public String toString() {
        if (parts == null || parts.isEmpty()) {
            return "";
        } else {
            return String.join(DELIMITER, parts);
        }
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final PropertyPath that = (PropertyPath) o;
        return parts.equals(that.parts);
    }

    public boolean equalsIgnoreCase(final PropertyPath o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
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

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    @Override
    @JsonIgnore
    public String getName() {
        return this.toString();
    }

    public static final class Builder {

        private List<String> parts;

        private Builder() {
        }

        private Builder(final PropertyPath propertyPath) {
            parts = propertyPath.parts;
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
