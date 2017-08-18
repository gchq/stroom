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

package stroom.entity.shared;

import stroom.query.api.v1.DocRef;
import stroom.util.shared.SharedObject;

public class DocumentServiceForkAction<D extends SharedObject> extends AbstractEntityAction<D> {
    private static final long serialVersionUID = 800905016214418723L;

    private D document;
    private String name;
    private DocRef destinationFolderRef;
    private PermissionInheritance permissionInheritance;

    public DocumentServiceForkAction() {
        // Default constructor necessary for GWT serialisation.
    }

    @SuppressWarnings("unchecked")
    public DocumentServiceForkAction(final Entity entity, final String name, final DocRef destinationFolderRef, final PermissionInheritance permissionInheritance) {
        this(DocRefUtil.create(entity), (D) entity, name, destinationFolderRef, permissionInheritance);
    }

    public DocumentServiceForkAction(final DocRef docRef, final D document, final String name, final DocRef destinationFolderRef, final PermissionInheritance permissionInheritance) {
        super(docRef, "Fork: " + docRef);
        this.document = document;
        this.name = name;
        this.destinationFolderRef = destinationFolderRef;
        this.permissionInheritance = permissionInheritance;
    }

    public D getDocument() {
        return document;
    }

    public String getName() {
        return name;
    }

    public DocRef getDestinationFolderRef() {
        return destinationFolderRef;
    }

    public PermissionInheritance getPermissionInheritance() {
        return permissionInheritance;
    }
}
