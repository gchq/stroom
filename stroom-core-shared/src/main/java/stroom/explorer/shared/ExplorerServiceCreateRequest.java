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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import stroom.docref.DocRef;

@JsonInclude(Include.NON_NULL)
public class ExplorerServiceCreateRequest {
    @JsonProperty
    private String docType;
    @JsonProperty
    private String docName;
    @JsonProperty
    private DocRef destinationFolderRef;
    @JsonProperty
    private PermissionInheritance permissionInheritance;

    public ExplorerServiceCreateRequest() {
    }

    @JsonCreator
    public ExplorerServiceCreateRequest(@JsonProperty("docType") final String docType,
                                        @JsonProperty("docName") final String docName,
                                        @JsonProperty("destinationFolderRef") final DocRef destinationFolderRef,
                                        @JsonProperty("permissionInheritance") final PermissionInheritance permissionInheritance) {
        this.docType = docType;
        this.docName = docName;
        this.destinationFolderRef = destinationFolderRef;
        this.permissionInheritance = permissionInheritance;
    }

    public String getDocType() {
        return docType;
    }

    public void setDocType(final String docType) {
        this.docType = docType;
    }

    public String getDocName() {
        return docName;
    }

    public void setDocName(final String docName) {
        this.docName = docName;
    }

    public DocRef getDestinationFolderRef() {
        return destinationFolderRef;
    }

    public void setDestinationFolderRef(final DocRef destinationFolderRef) {
        this.destinationFolderRef = destinationFolderRef;
    }

    public PermissionInheritance getPermissionInheritance() {
        return permissionInheritance;
    }

    public void setPermissionInheritance(final PermissionInheritance permissionInheritance) {
        this.permissionInheritance = permissionInheritance;
    }
}