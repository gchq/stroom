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
 */

package stroom.entity.shared;

import stroom.query.api.v2.DocRef;

public class EntityServiceCopyAction<E extends Entity> extends AbstractEntityAction<E> {
    private static final long serialVersionUID = 800905016214418723L;
    private DocRef folder;
    private String name;
    private PermissionInheritance permissionInheritance;

    public EntityServiceCopyAction() {
        // Default constructor necessary for GWT serialisation.
    }

    public EntityServiceCopyAction(final E entity, final DocRef folder, final String name, final PermissionInheritance permissionInheritance) {
        super(entity, "Copy: " + entity + " - " + name);
        this.folder = folder;
        this.name = name;
        this.permissionInheritance = permissionInheritance;
    }

    public DocRef getFolder() {
        return folder;
    }

    public String getName() {
        return name;
    }

    public PermissionInheritance getPermissionInheritance() {
        return permissionInheritance;
    }
}
