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

import java.util.List;

public class ExplorerServiceMoveRequest {
    private List<DocRef> docRefs;
    private DocRef destinationFolderRef;
    private PermissionInheritance permissionInheritance;

    public ExplorerServiceMoveRequest() {
        // Default constructor necessary for GWT serialisation.
    }

    public ExplorerServiceMoveRequest(final List<DocRef> docRefs, final DocRef destinationFolderRef, final PermissionInheritance permissionInheritance) {
        this.docRefs = docRefs;
        this.destinationFolderRef = destinationFolderRef;
        this.permissionInheritance = permissionInheritance;
    }

    public List<DocRef> getDocRefs() {
        return docRefs;
    }

    public void setDocRefs(final List<DocRef> docRefs) {
        this.docRefs = docRefs;
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