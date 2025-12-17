/*
 * Copyright 2016-2025 Crown Copyright
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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Class for representing a path to a property in an object tree, i.e
 * stroom.node.name
 * The aim is to break the dot delimited path strings into its parts to
 * reduce the memory overhead of holding all the paths as many parts are similar.
 * Also makes it easier to merge property paths together.
 */
@JsonInclude(Include.NON_NULL)
public class PropertyPath implements Comparable<PropertyPath> {
//    public class PropertyPath implements Comparable<stroom.util.shared.PropertyPath>, HasName {

    private static final String DELIMITER = ".";
    private static final String DELIMITER_REGEX = "\\" + DELIMITER;

    private static final PropertyPath EMPTY_INSTANCE = new PropertyPath(Collections.emptyList());

    // Allows us to hold only one instance of each equal list to reduce mem use
    private static final Map<List<String>, List<String>> PARENT_PARTS_MAP = new ConcurrentHashMap<>();

    // Held in part form to reduce memory overhead as some parts will be used
    // many times over all the config objects
    @JsonProperty("parentParts")
    private final List<String> parentParts;
    @JsonProperty("leafPart")
    private final String leafPart;

    // Cache the hash to speed up map look-ups
    @JsonIgnore
    private final int hashCode;

    PropertyPath(final List<String> parts) {
        if (parts == null || parts.isEmpty()) {
            this.parentParts = Collections.emptyList();
            this.leafPart = null;
        } else {
            validateParts(parts);
            final List<String> parentParts = extractParentParts(parts);
            this.parentParts = getInternedParentParts(parentParts);
            this.leafPart = getInternedLeafPart(parts);
        }

        hashCode = buildHashCode(this.parentParts, leafPart);
    }

    @JsonCreator
    PropertyPath(@JsonProperty("parentParts") final List<String> parentParts,
                 @JsonProperty("leafPart") final String leafPart) {
        this.parentParts = getInternedParentParts(parentParts);
        this.leafPart = leafPart != null
                ? leafPart.intern()
                : null;

        hashCode = buildHashCode(this.parentParts, this.leafPart);
    }

    private String getInternedLeafPart(final List<String> allParts) {
        if (allParts == null || allParts.isEmpty()) {
            return null;
        } else {
            final String leafPart = allParts.get(allParts.size() - 1);
            validatePart(leafPart);
            return leafPart.intern();
        }
    }

    private List<String> extractParentParts(final List<String> allParts) {
        if (allParts == null || allParts.isEmpty()) {
            return Collections.emptyList();
        } else {
            validateParts(allParts);
            if (allParts.size() == 1) {
                return Collections.emptyList();
            } else {
                return allParts.subList(0, allParts.size() - 1);
            }
        }
    }

    private List<String> getInternedParentParts(final List<String> parentParts) {
        if (parentParts == null || parentParts.isEmpty()) {
            return Collections.emptyList();
        } else {
            // Do a simple get first to avoid having to do the intern in case the key is not present
            List<String> cachedParentParts = PARENT_PARTS_MAP.get(parentParts);
            if (cachedParentParts == null) {
                // Intern each part so that we only have one instance of each part
                final List<String> internedParts = internParts(parentParts);
                cachedParentParts = PARENT_PARTS_MAP.computeIfAbsent(internedParts, k -> internedParts);
            }
            return cachedParentParts;
        }
    }

    private List<String> internParts(final List<String> parts) {
        if (parts == null || parts.isEmpty()) {
            return parts;
        } else {
            // The parts may not have come from string literals so intern them as we have lots
            // of duplication of the parts
            return parts.stream()
                    .map(String::intern)
                    .collect(Collectors.toList());
        }
    }

    private static List<String> combineLists(final List<String> parts1, final List<String> parts2) {
        if (parts1 == null || parts1.isEmpty()) {
            return parts2;
        } else if (parts2 == null || parts2.isEmpty()) {
            return parts1;
        } else {
            final List<String> allParts = new ArrayList<>(parts1.size() + parts2.size());
            allParts.addAll(parts1);
            allParts.addAll(parts2);
            return allParts;
        }
    }

    private PropertyPath(final List<String> parts1, final List<String> parts2) {
        this(combineLists(parts1, parts2));
    }

//    private PropertyPath(final List<String> parts1, final String finalPart) {
//        final List<String> mutableParts = new ArrayList<>(parts1.size() + 1);
//        mutableParts.addAll(parts1);
//        mutableParts.add(finalPart);
//        parts = internParts(mutableParts);
//        validateParts(parts);
//        hashCode = buildHashCode(this.parts);
//    }

    private int buildHashCode(final List<String> parentParts, final String leafPart) {
        return Objects.hash(parentParts, leafPart);
    }

    public static PropertyPath blank() {
        return EMPTY_INSTANCE;
    }

    @JsonIgnore
    public boolean isBlank() {
        return parentParts.isEmpty() && leafPart == null;
    }

    public boolean containsPart(final String part) {
        return parentParts.contains(part) || Objects.equals(leafPart, part);
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
        if (parts != null) {
            for (final String part : parts) {
                validatePart(part);
            }
        }
    }

    private static void validatePart(final String part) {
        if (part == null || part.isEmpty()) {
            throw new RuntimeException("Null or empty path part");
        }
    }

    List<String> getParts() {
        if (parentParts == null || parentParts.isEmpty()) {
            return leafPart == null || leafPart.isEmpty()
                    ? Collections.emptyList()
                    : Collections.singletonList(leafPart);
        } else {
            return Stream.concat(parentParts.stream(), Stream.of(leafPart))
                    .collect(Collectors.toList());
        }
    }

    List<String> getParentParts() {
        return parentParts;
    }

    String getLeafPart() {
        return leafPart;
    }

    /**
     * Merge otherPath onto the end of this and return a new {@link PropertyPath}
     */
    public PropertyPath merge(final PropertyPath otherPath) {
        if (otherPath == null || otherPath.isBlank()) {
            return this;
        } else {
            return new PropertyPath(this.getParts(), otherPath.getParts());
        }
    }

    /**
     * Merge part onto the end of this and return a new {@link PropertyPath}
     */
    public PropertyPath merge(final String otherPart) {
        if (otherPart == null || otherPart.isEmpty()) {
            return this;
        } else {
            return new PropertyPath(this.getParts(), otherPart);
        }
    }

    /**
     * Merge parts onto the end of this and return a new {@link PropertyPath}
     */
    public PropertyPath merge(final String... parts) {
        if (parts == null || parts.length == 0) {
            return this;
        } else {
            return new PropertyPath(this.getParts(), Arrays.asList(parts));
        }
    }

    @JsonIgnore
    public Optional<PropertyPath> getParent() {
        if (parentParts.isEmpty()) {
            return Optional.empty();
        } else {
            return Optional.of(new PropertyPath(parentParts));
        }
    }

    /**
     * @return The property name from a property path, i.e. "name" from "stroom.node.name"
     */
    @JsonIgnore
    public String getPropertyName() {
        if (leafPart == null || leafPart.isEmpty()) {
            throw new RuntimeException("Unable to get property name from empty path");
        }
        return leafPart;
    }

    /**
     * @return The parent property name from a property path, i.e. "node" from "stroom.node.name".
     * Throws a {@link RuntimeException} if the path is empty or there is no parent.
     */
    @JsonIgnore
    public Optional<String> getParentPropertyName() {
        if (parentParts.isEmpty()) {
            return Optional.empty();
        } else {
            return Optional.ofNullable(parentParts.get(parentParts.size() - 1));
        }
    }

    @Override
    public int compareTo(final PropertyPath other) {
        return toString().compareTo(other.toString());
    }

    @Override
    public String toString() {
        return delimitedBy(DELIMITER);
    }

    public String delimitedBy(final String delimiter) {
        if (parentParts.isEmpty() && leafPart == null) {
            return "";
        } else {
            return String.join(delimiter, getParts());
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
        return Objects.equals(parentParts, that.parentParts)
               && Objects.equals(leafPart, that.leafPart);
    }

    public boolean equalsIgnoreCase(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final PropertyPath that = (PropertyPath) o;
        if (this.leafPart == null && that.leafPart == null) {
            return areParentPartsEqualIgnoringCase(this.parentParts, that.parentParts);
        } else if (this.leafPart == null && that.leafPart != null) {
            return false;
        } else if (this.leafPart != null && that.leafPart == null) {
            return false;
        } else {
            return this.leafPart.equalsIgnoreCase(that.leafPart)
                   && areParentPartsEqualIgnoringCase(this.parentParts, that.parentParts);
        }
    }

    private boolean areParentPartsEqualIgnoringCase(final List<String> parentParts1,
                                                    final List<String> parentParts2) {
        if (parentParts1 == null && parentParts2 == null) {
            return true;
        } else if (parentParts1 == null) {
            return false;
        } else if (parentParts2 == null) {
            return false;
        } else if (parentParts1.size() != parentParts2.size()) {
            return false;
        } else {
            for (int i = 0; i < parentParts1.size(); i++) {
                if (!parentParts1.get(i).equalsIgnoreCase(parentParts2.get(i))) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    @JsonIgnore
    public String getName() {
        return this.toString();
    }


    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~


    public static final class Builder {

        private List<String> parts;

        private Builder() {
        }

        private Builder(final PropertyPath propertyPath) {
            parts = propertyPath.getParts();
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
