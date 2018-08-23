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

import stroom.task.shared.Action;
import stroom.entity.shared.PermissionInheritance;
import stroom.docref.DocRef;

import java.util.List;

public class ExplorerServiceMoveAction extends Action<BulkActionResult> {
    private static final long serialVersionUID = 800905016214418723L;

    private List<DocRef> docRefs;
    private DocRef destinationFolderRef;
    private PermissionInheritance permissionInheritance;

    public ExplorerServiceMoveAction() {
        // Default constructor necessary for GWT serialisation.
    }

    public ExplorerServiceMoveAction(final List<DocRef> docRefs, final DocRef destinationFolderRef, final PermissionInheritance permissionInheritance) {
        this.docRefs = docRefs;
        this.destinationFolderRef = destinationFolderRef;
        this.permissionInheritance = permissionInheritance;
    }

    public List<DocRef> getDocRefs() {
        return docRefs;
    }

    public DocRef getDestinationFolderRef() {
        return destinationFolderRef;
    }

    public PermissionInheritance getPermissionInheritance() {
        return permissionInheritance;
    }

    @Override
    public String getTaskName() {
        return "Move " + docRefs.size() + " documents to '" + destinationFolderRef + "'";
    }
}