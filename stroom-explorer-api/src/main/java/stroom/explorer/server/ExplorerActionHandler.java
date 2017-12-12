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

package stroom.explorer.server;

import stroom.query.api.v2.DocRef;
import stroom.util.shared.DocRefInfo;

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
     * @param uuid             The uuid of the document you want to copy.
     * @param parentFolderUUID The uuid of the parent folder that you want to create the copy in.
     * @return A doc ref for the new document copy.
     */
    DocRef copyDocument(String uuid, String parentFolderUUID);

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
