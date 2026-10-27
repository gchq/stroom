/*
 * Copyright 2026 Crown Copyright
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

package stroom.ai.shared;

import stroom.docref.DocRef;
import stroom.explorer.shared.PermissionInheritance;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/// The user's confirmation of a search extraction to write (see [AiCreateExtraction]): the name and folder they
/// chose, and the hash of what they were shown, so that only that is written.
@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder(alphabetic = true)
public class AiWriteExtractionRequest {

    @JsonProperty
    private final String name;
    @JsonProperty
    private final DocRef folder;
    @JsonProperty
    private final PermissionInheritance permissionInheritance;
    @JsonProperty
    private final String contentHash;

    /// @param name                  The name of the new pipeline and its XSLT.
    /// @param folder                The folder to make them in.
    /// @param permissionInheritance How they take permissions from the folder.
    /// @param contentHash           The hash of what the user was shown (see [AiCreateExtraction#getContentHash()]).
    @JsonCreator
    public AiWriteExtractionRequest(@JsonProperty("name") final String name,
                                    @JsonProperty("folder") final DocRef folder,
                                    @JsonProperty("permissionInheritance")
                                    final PermissionInheritance permissionInheritance,
                                    @JsonProperty("contentHash") final String contentHash) {
        this.name = name;
        this.folder = folder;
        this.permissionInheritance = permissionInheritance;
        this.contentHash = contentHash;
    }

    /// @return The name of the new pipeline and its XSLT.
    public String getName() {
        return name;
    }

    /// @return The folder to make them in.
    public DocRef getFolder() {
        return folder;
    }

    /// @return How they take permissions from the folder.
    public PermissionInheritance getPermissionInheritance() {
        return permissionInheritance;
    }

    /// @return The hash of what the user was shown.
    public String getContentHash() {
        return contentHash;
    }
}
