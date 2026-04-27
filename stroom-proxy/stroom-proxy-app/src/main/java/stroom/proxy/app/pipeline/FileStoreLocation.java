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

package stroom.proxy.app.pipeline;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

/**
 * A stable reference to a file-group location in a named {@link FileStore}.
 * <p>
 * Queue messages should carry this value rather than moving the underlying data.
 * The location is intentionally URI based so the contract can support non-file
 * stores in future while initially supporting local/shared filesystem storage.
 * </p>
 */
@JsonPropertyOrder(alphabetic = true)
public record FileStoreLocation(
        @JsonProperty(value = "storeName", required = true)
        String storeName,
        @JsonProperty(value = "locationType", required = true)
        LocationType locationType,
        @JsonProperty(value = "uri", required = true)
        String uri,
        @JsonProperty("attributes")
        Map<String, String> attributes) {

    private static final String FILE_URI_SCHEME = "file:";

    @JsonCreator
    public FileStoreLocation {
        storeName = requireNonBlank(storeName, "storeName");
        locationType = Objects.requireNonNull(locationType, "locationType");
        uri = requireNonBlank(uri, "uri");
        attributes = attributes == null || attributes.isEmpty()
                ? Map.of()
                : Map.copyOf(attributes);

        if (LocationType.LOCAL_FILESYSTEM.equals(locationType)
            && !uri.regionMatches(true, 0, FILE_URI_SCHEME, 0, FILE_URI_SCHEME.length())) {
            throw new IllegalArgumentException("LOCAL_FILESYSTEM locations must use a file URI");
        }
    }

    public static FileStoreLocation localFileSystem(final String storeName,
                                                    final Path path) {
        Objects.requireNonNull(path, "path");
        return new FileStoreLocation(
                storeName,
                LocationType.LOCAL_FILESYSTEM,
                path.toAbsolutePath().normalize().toUri().toString(),
                Map.of());
    }

    @JsonIgnore
    public boolean isLocalFileSystem() {
        return LocationType.LOCAL_FILESYSTEM.equals(locationType);
    }

    private static String requireNonBlank(final String value,
                                          final String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }

    public enum LocationType {
        LOCAL_FILESYSTEM
    }
}
