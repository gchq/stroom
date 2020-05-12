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

package stroom.explorer.api;

import stroom.docref.DocRef;
import stroom.docref.DocRefInfo;
import stroom.util.shared.HasDependencies;

import java.util.Set;

/**
 * This interface is intended to be used by the explorer for document store operations that need not know much about the
 * documents that are stored just how to create, copy, move and delete them.
 */
public interface ExplorerActionHandler extends HasDocumentType, HasDependencies {
    /**
     * Called to create a new item in this document store.
     *
     * @param name The name of the document to be created.
     * @return A doc ref for the newly created document.
     */
    DocRef createDocument(String name);

    /**
     * Copy an existing document identified by uuid, to the specified location.
     *
     * @param docRef        The docref of the document you want to copy.
     * @param existingNames Names of documents that already exist in the destination folder.
     * @return A doc ref for the new document copy.
     */
    DocRef copyDocument(DocRef docRef,
                        Set<String> existingNames);

    /**
     * Move an existing document identified by uuid, to the specified location.
     *
     * @param uuid The uuid of the document you want to move.
     * @return A doc ref for the moved document.
     */
    DocRef moveDocument(String uuid);

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
     *
     * @param uuid The UUID to return the information for
     * @return The Audit information about the given DocRef.
     */
    DocRefInfo info(String uuid);
}
