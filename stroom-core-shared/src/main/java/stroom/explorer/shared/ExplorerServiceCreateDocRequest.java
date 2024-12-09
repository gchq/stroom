/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.explorer.shared;

import stroom.docref.DocRef;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_NULL)
public class ExplorerServiceCreateDocRequest {

    @JsonProperty
    private final DocRef docRef;
    @JsonProperty
    private final ExplorerNode destinationFolder;
    @JsonProperty
    private final PermissionInheritance permissionInheritance;

    @JsonCreator
    public ExplorerServiceCreateDocRequest(
            @JsonProperty("docRef") final DocRef docRef,
            @JsonProperty("destinationFolder") final ExplorerNode destinationFolder,
            @JsonProperty("permissionInheritance") final PermissionInheritance permissionInheritance) {
        this.docRef = docRef;
        this.destinationFolder = destinationFolder;
        this.permissionInheritance = permissionInheritance;
    }

    public DocRef getDocRef() {
        return docRef;
    }

    public ExplorerNode getDestinationFolder() {
        return destinationFolder;
    }

    public PermissionInheritance getPermissionInheritance() {
        return permissionInheritance;
    }
}
