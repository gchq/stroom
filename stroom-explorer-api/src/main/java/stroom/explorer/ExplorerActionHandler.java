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

package stroom.explorer;

import stroom.query.api.v2.DocRef;
import stroom.query.api.v2.DocRefInfo;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

/**
 * This interface is intended to be used by the explorer for document store operations that need not know much about the
 * documents that are stored just how to create, copy, move and delete them.
 */
public interface ExplorerActionHandler {
    /**
     * Called to create a new item in this document store.
     *
     * @param name             The name of the document to be created.
     * @param parentFolderUUID The parent folder that the item wil be created in. This is used to check create permissions.
     * @return A doc ref for the newly created document.
     */
    DocRef createDocument(String name, String parentFolderUUID);

    /**
     * Copy an existing document identified by uuid, to the specified location.
     *
     * @param originalUuid     The uuid of the document you want to copy.
     * @param copyUuid         The uuid of the intended copy
     * @param otherCopiesByOriginalUUid For bulk copy operations, this contains all other copies being made.
     *                                  This allows the sub service to repoint dependencies on this copy to other copies being made.
     * @param parentFolderUUID The uuid of the parent folder that you want to create the copy in.
     * @return A doc ref for the new document copy.
     */
    DocRef copyDocument(String originalUuid,
                        String copyUuid,
                        Map<String, String> otherCopiesByOriginalUUid,
                        String parentFolderUUID);

    /**
     * Default form of the copy function, allow clients to let a UUID be made for them.
     * Assumes singular copy, no bulk repointing will happen
     * @param uuid              The uuid of the document you want to copy.
     * @param parentFolderUUID  The uuid of the parent folder that you want to create the copy in.
     * @return
     */
    default DocRef copyDocument(String uuid, String parentFolderUUID) {
        return copyDocument(uuid, UUID.randomUUID().toString(), Collections.emptyMap(), parentFolderUUID);
    }

    /**
     * Move an existing document identified by uuid, to the specified location.
     *
     * @param uuid             The uuid of the document you want to move.
     * @param parentFolderUUID The uuid of the parent folder that you want to move the document to.
     * @return A doc ref for the moved document.
     */
    DocRef moveDocument(String uuid, String parentFolderUUID);

    /**
     * Change the name of an existing document identified by uuid, to the specified name.
     *
     * @param uuid The uuid of the document you want to rename.
     * @param name The new name of the document.
     * @return A doc ref for the renamed document.
     */
    DocRef renameDocument(String uuid, String name);

    /**
     * Delete an existing document identified by uuid.
     *
     * @param uuid The uuid of the document you want to delete.
     */
    void deleteDocument(String uuid);

    /**
     * Retrieve the audit information for a particular doc ref
     * @param uuid The UUID to return the information for
     * @return The Audit information about the given DocRef.
     */
    DocRefInfo info(String uuid);
}
