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
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Represents an explorer tree path to a document or folder
 */
@JsonPropertyOrder(alphabetic = true)
public class DocPath {

    private static final String DELIMITER = "/";
    private static final String DELIMITER_REGEX = DELIMITER;
    private static final DocPath EMPTY_INSTANCE = new DocPath(Collections.emptyList());
    private static final boolean DEFAULT_ABSOLUTE_VALUE = true;

    // It is split into parentParts and leafPart as that is how it is done in PropertyPath
    // which caches the parentParts lists as they are immutable.  We probably can't cache
    // exp tree paths, but leaving it like this for now as it doesn't make much difference.
    @JsonProperty("parentParts")
    private final List<String> parentParts;
    @JsonProperty("leafPart")
    private final String leafPart;
    @JsonProperty("absolute")
    private final boolean absolute;

    // Lazily cache the hash to speed up map look-ups
    @JsonIgnore
    private int hashCode = 0;
    @JsonIgnore
    private boolean hashIsZero = false;

    /**
     * Absolute by default
     */
    DocPath(final List<String> parts) {
        this(parts, DEFAULT_ABSOLUTE_VALUE);
    }

    DocPath(final List<String> parts, final boolean absolute) {
        if (parts == null || parts.isEmpty()) {
            this.parentParts = Collections.emptyList();
            this.leafPart = null;
        } else {
            validateParts(parts);
            this.parentParts = extractParentParts(parts);
            this.leafPart = getLeafPart(parts);
        }
        this.absolute = absolute;
    }

    @JsonCreator
    DocPath(@JsonProperty("parentParts") final List<String> parentParts,
            @JsonProperty("leafPart") final String leafPart,
            @JsonProperty("absolute") final boolean absolute) {
        this.parentParts = parentParts;
        this.leafPart = leafPart;
        this.absolute = absolute;
    }

    private DocPath(final List<String> parts1, final List<String> parts2) {
        this(combineLists(parts1, parts2));
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

    /**
     * Construct an absolute {@link DocPath} from parts.
     */
    public static DocPath fromParts(final List<String> parts) {
        return new DocPath(parts);
    }

    /**
     * Construct an absolute/relative {@link DocPath} from parts.
     */
    public static DocPath fromParts(final boolean absolute, final List<String> parts) {
        return new DocPath(parts, absolute);
    }

    /**
     * Construct an absolute {@link DocPath} from parts.
     */
    public static DocPath fromParts(final String... parts) {
        return fromParts(DEFAULT_ABSOLUTE_VALUE, parts);
    }

    /**
     * Construct an absolute/relative {@link DocPath} from parts.
     */
    public static DocPath fromParts(final boolean absolute, final String... parts) {
        if (parts == null || parts.length == 0) {
            return EMPTY_INSTANCE;
        } else {
            return new DocPath(List.of(parts), absolute);
        }
    }

    /**
     * Construct a {@link DocPath} from a path string, e.g. {@code /foo/bar}.
     * A leading {@code /} means the path is absolute. Any trailing {@code /}
     * will be stripped.
     */
    public static DocPath fromPathString(final String pathString) {
        if (pathString == null || pathString.isEmpty() || DELIMITER.equals(pathString)) {
            return EMPTY_INSTANCE;
        } else {
            String str = pathString.trim();
            final boolean absolute = str.startsWith(DELIMITER);
            if (absolute) {
                str = str.substring(1);
            }
            if (str.endsWith(DELIMITER)) {
                str = str.substring(0, str.length() - 1);
            }

            // Can't user Pattern.split cos of GWT :-(
            return DocPath.fromParts(absolute, str.split(DELIMITER_REGEX));
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

    /**
     * @return The path parts that make up the parent node.
     */
    public List<String> getParentParts() {
        return parentParts;
    }

    /**
     * @return The root part unless it is an empty path
     */
    public Optional<String> getRoot() {
        return parentParts.isEmpty()
                ? Optional.empty()
                : Optional.of(parentParts.get(0));
    }

    /**
     * @return The leaf part unless it is an empty path
     */
    public Optional<String> getLeaf() {
        return Optional.ofNullable(leafPart);
    }

    /**
     * @return The leaf part or null of this is an empty path
     */
    String getLeafPart() {
        return leafPart;
    }

    @JsonIgnore
    public List<String> getAllParts() {
        return Stream.concat(parentParts.stream(), Stream.of(leafPart))
                .collect(Collectors.toList());
    }

    public boolean isAbsolute() {
        return absolute;
    }

    public boolean isRelative() {
        return !absolute;
    }

    public DocPath toAbsolutePath() {
        if (absolute) {
            return this;
        } else {
            return new DocPath(parentParts, leafPart, DEFAULT_ABSOLUTE_VALUE);
        }
    }

    public DocPath toRelativePath() {
        if (!absolute) {
            return this;
        } else {
            return new DocPath(parentParts, leafPart, false);
        }
    }

    public static DocPath blank() {
        return EMPTY_INSTANCE;
    }

    @JsonIgnore
    public boolean isBlank() {
        return parentParts.isEmpty() && leafPart == null;
    }

    public void forEach(final PartPartsConsumer partPartsConsumer) {
        int idx = 0;
        if (parentParts != null) {
            for (final String part : parentParts) {
                partPartsConsumer.accept(idx++, part);
            }
        }
        if (leafPart != null) {
            partPartsConsumer.accept(idx++, leafPart);
        }
    }

    /**
     * Append otherPath onto the end of this and return a new {@link DocPath}
     */
    public DocPath append(final DocPath otherPath) {
        if (otherPath == null || otherPath.isBlank()) {
            return this;
        } else {
            if (otherPath.isAbsolute()) {
                throw new IllegalArgumentException("otherPath can't be absolute");
            }
            return new DocPath(this.getParts(), otherPath.getParts());
        }
    }

    /**
     * Append part onto the end of this and return a new {@link DocPath}
     */
    public DocPath append(final String otherPart) {
        if (otherPart == null || otherPart.isEmpty()) {
            return this;
        } else {
            return new DocPath(this.getParts(), otherPart, absolute);
        }
    }

    /**
     * Append parts onto the end of this and return a new {@link DocPath}
     */
    public DocPath append(final String... parts) {
        if (parts == null || parts.length == 0) {
            return this;
        } else {
            return new DocPath(this.getParts(), Arrays.asList(parts));
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
                return allParts.subList(0, allParts.size() - 1)
                        .stream()
                        .map(String::trim)
                        .collect(Collectors.toList());
            }
        }
    }

    private String getLeafPart(final List<String> allParts) {
        if (allParts == null || allParts.isEmpty()) {
            return null;
        } else {
            final String leafPart = allParts.get(allParts.size() - 1);
            validatePart(leafPart);
            return leafPart.trim();
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

    public String delimitedBy(final String delimiter) {
        if (parentParts.isEmpty() && leafPart == null) {
            return absolute
                    ? DELIMITER
                    : "";
        } else {
            if (absolute) {
                return DELIMITER + String.join(delimiter, getParts());
            } else {
                return String.join(delimiter, getParts());
            }
        }
    }

    public boolean containsPart(final String part) {
        return parentParts.contains(part) || Objects.equals(leafPart, part);
    }

    /**
     * @return The parent part name from a doc path, i.e. "Farm" from "Animals/Farm/Horse".
     * Throws a {@link RuntimeException} if the path is empty or there is no parent.
     */
    @JsonIgnore
    public Optional<String> getParentName() {
        if (parentParts.isEmpty()) {
            return Optional.empty();
        } else {
            return Optional.ofNullable(parentParts.get(parentParts.size() - 1));
        }
    }

    @JsonIgnore
    public Optional<DocPath> getParent() {
        if (parentParts.isEmpty()) {
            return Optional.empty();
        } else {
            return Optional.of(new DocPath(parentParts));
        }
    }

    @Override
    public String toString() {
        return delimitedBy(DELIMITER);
    }

    @Override
    public boolean equals(final Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || getClass() != object.getClass()) {
            return false;
        }
        final DocPath docPath = (DocPath) object;
        return Objects.equals(parentParts, docPath.parentParts)
               && Objects.equals(leafPart, docPath.leafPart)
               && absolute == docPath.absolute;
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

    @SuppressWarnings("ConstantValue") // For clarity
    public boolean equalsIgnoreCase(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DocPath that = (DocPath) o;
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

    @Override
    public int hashCode() {
        int hashCode = this.hashCode;
        if (hashCode == 0 && !hashIsZero) {
            hashCode = Objects.hash(parentParts, leafPart);
            if (hashCode == 0) {
                hashIsZero = true;
            } else {
                this.hashCode = hashCode;
            }
        }
        return hashCode;
    }

    public static DocPath.Builder builder() {
        return new DocPath.Builder();
    }


    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~


    public static final class Builder {

        private boolean absolute = DEFAULT_ABSOLUTE_VALUE;
        private List<String> parts;

        private Builder() {
        }

        private Builder(final DocPath docPath) {
            parts = docPath.getParts();
            absolute = docPath.isAbsolute();
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

        public Builder absolute(final boolean absolute) {
            this.absolute = absolute;
            return this;
        }

        public Builder absolute() {
            this.absolute = true;
            return this;
        }

        public Builder relative() {
            this.absolute = false;
            return this;
        }

        public DocPath build() {
            return new DocPath(parts);
        }
    }


    // --------------------------------------------------------------------------------


    @FunctionalInterface
    public interface PartPartsConsumer {

        void accept(final int idx, final String pathPart);
    }
}
