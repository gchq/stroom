/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.docstore.impl;

import stroom.docref.DocRef;
import stroom.docstore.shared.AuditAction;
import stroom.docstore.shared.DocAuditEntry;
import stroom.importexport.api.ImportExportDocument;
import stroom.util.shared.ResultPage;
import stroom.util.shared.UserRef;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Abstraction over the underlying document storage mechanism (database, filesystem, or in-memory).
 * Each implementation is responsible for CRUD operations, auditing, listing, and search
 * across document types. Implementations must handle their own concurrency control
 * (e.g. database transactions, file-system locks).
 */
public interface Persistence {

    /**
     * Check whether a non-deleted document exists for the given reference.
     *
     * @param docRef the document reference to check
     * @return {@code true} if the document exists and has not been deleted
     */
    boolean exists(DocRef docRef);

    /**
     * Delete a document. Database implementations use soft-delete (setting a deleted timestamp);
     * filesystem implementations physically remove the files.
     *
     * @param docRef  the document to delete
     * @param userRef the user performing the deletion (recorded for audit)
     */
    void delete(DocRef docRef, UserRef userRef);

    /**
     * Read all asset data for a document.
     *
     * @param docRef the document to read
     * @return the document's assets (meta, data, etc.), or {@code null} / exception if not found
     * @throws IOException if there is an error reading the data
     */
    ImportExportDocument read(DocRef docRef) throws IOException;

    /**
     * Write document data to storage.
     *
     * @param docRef                 the document reference
     * @param auditAction            the audit action being performed
     * @param userRef                the user performing the action
     * @param importExportDocument   the document data to write
     * @param expectedVersion        for UPDATE/RENAME: the version the caller expects to be current
     *                               (throws {@link stroom.util.exception.DataChangedException} if stale).
     *                               For CREATE/COPY/IMPORT: {@code null} (no version check).
     * @param newVersion             the new version to set on the document after a successful write
     */
    void write(DocRef docRef, AuditAction auditAction, UserRef userRef,
               ImportExportDocument importExportDocument,
               String expectedVersion, String newVersion) throws IOException;

    /**
     * List all non-deleted documents of the given type.
     *
     * @param type the document type to list
     * @return the matching document references, or an empty list if none found
     */
    List<DocRef> list(String type);

    /**
     * Find document references that are embedded within the given parent document.
     * For example, a pipeline document may embed references to XSLT or dictionary documents.
     *
     * @param parent the parent document to search within
     * @return the embedded document references, or an empty list if none found
     */
    List<DocRef> findDocRefsEmbeddedIn(final DocRef parent);

    /**
     * List documents across multiple types.
     * If types is null or empty, returns an empty list.
     */
    List<DocRef> list(Collection<String> types);

    /**
     * Find docRefs by name and type. Name can be optionally wild carded using '*' to match 0-many chars.
     */
    List<DocRef> find(String type,
                      String nameFilter,
                      boolean allowWildCards);

    /**
     * Find docRefs by type and one or more nameFilters.
     * nameFilters can be optionally wild carded using '*' to match 0-many chars.
     */
    List<DocRef> find(String type,
                      List<String> nameFilters,
                      boolean allowWildCards);

    /**
     * Find docRefs by name across multiple types. If types is null or empty, searches ALL types.
     * This is the cross-type variant used by caches and services.
     */
    List<DocRef> find(Collection<String> types,
                      List<String> nameFilters,
                      boolean allowWildCards);

    /**
     * Get the current name for the supplied doc ref.
     *
     * @param docRef The doc ref to get the name for.
     * @return The name or empty if not found.
     */
    Optional<String> getName(DocRef docRef);

    /**
     * Get document audit entries by UUID without needing to know the document type.
     * Returns empty result page if the document doesn't exist.
     */
    ResultPage<DocAuditEntry> getAuditInfo(DocRef docRef);
}
