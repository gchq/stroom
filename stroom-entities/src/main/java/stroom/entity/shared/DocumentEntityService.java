/*
 * Copyright 2016 Crown Copyright
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

import stroom.entity.shared.ImportState.ImportMode;
import stroom.util.shared.Message;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface DocumentEntityService<E extends DocumentEntity> extends BaseEntityService<E>, HasLoadByUuid<E>, ProvidesNamePattern {
    E create(DocRef folder, String name) throws RuntimeException;

    E create(DocRef folder, String name, PermissionInheritance permissionInheritance) throws RuntimeException;

    E loadByName(DocRef folder, String name) throws RuntimeException;

    E loadByName(DocRef folder, String name, Set<String> fetchSet) throws RuntimeException;

    /**
     * Copy the entity to the specified folder with the specified name.
     */
    E copy(E entity, DocRef folder, String name, PermissionInheritance permissionInheritance);

    /**
     * Move the entity to the specified folder.
     */
    E move(E entity, DocRef folder, PermissionInheritance permissionInheritance);

    List<E> findByFolder(DocRef folder, Set<String> fetchSet) throws RuntimeException;

    DocRef importDocument(Folder folder, Map<String, String> dataMap, final ImportState importState, final ImportMode importMode);

    Map<String, String> exportDocument(DocRef docRef, boolean omitAuditFields, List<Message> messageList);

    /**
     * Get a list of all possible permissions for the associated document type.
     *
     * @return A list of all possible permissions for the associated document
     * type.
     */
    String[] getPermissions();
}
