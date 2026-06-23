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

import stroom.docstore.shared.DocAuditEntry;
import stroom.docref.DocRef;
import stroom.docstore.api.RWLockFactory;
import stroom.docstore.shared.AuditAction;
import stroom.importexport.api.ImportExportDocument;
import stroom.util.shared.ResultPage;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface Persistence {

    boolean exists(DocRef docRef);

    void delete(DocRef docRef);

    ImportExportDocument read(DocRef docRef) throws IOException;

    void write(DocRef docRef, AuditAction auditAction, ImportExportDocument importExportDocument) throws IOException;

    List<DocRef> list(String type);

    RWLockFactory getLockFactory();

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

//    /**
//     * Get all documents for a given name.
//     *
//     * @param name The name to filter documents by.
//     * @return A list of documents with the supplied name.
//     */
//    List<DocRef> findByName(String name);

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
