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

package stroom.docstore.api;

import stroom.docref.DocRef;
import stroom.importexport.shared.Dependency;
import stroom.importexport.shared.DependencyCriteria;
import stroom.util.shared.ResultPage;

import java.util.Map;
import java.util.Set;

/**
 * Service for managing document dependency relationships.
 * Dependencies are persisted in the doc_dependency table and kept current by
 * direct calls from the document write paths (e.g. the docstore StoreImpl) as
 * documents are created, updated and deleted. Calling directly rather than via
 * cluster-wide entity events ensures the DB is mutated once per change rather
 * than once per node.
 */
public interface DocDependencyService {

    /**
     * Update dependencies for a single document (called on doc save).
     * Re-computes the document's dependencies and replaces all stored edges.
     */
    void updateDependencies(DocRef docRef);

    /**
     * Remove all dependency edges from a document (called on doc delete).
     */
    void removeDependencies(DocRef docRef);

    /**
     * Propagate a name change to all edges that reference this doc.
     * Updates both from_name (where from_uuid matches) and to_name (where to_uuid matches).
     */
    void propagateName(DocRef docRef);

    /**
     * Query dependencies from the DB table (paginated, filtered, sorted).
     * The filter predicate is applied in Java after rows are fetched from
     * the database, allowing callers to apply checks that cannot be expressed
     * in SQL (e.g. document permission checks). Pagination is applied
     * <b>after</b> filtering so page sizes and totals are correct.
     *
     * @param criteria the search/sort/page criteria
     */
    ResultPage<Dependency> fetchDependencies(DependencyCriteria criteria);

    /**
     * Get all broken dependencies from the DB table.
     */
    Map<DocRef, Set<DocRef>> fetchBrokenDependencies(final Set<String> pseudoRefUuids);

    void invalidateBrokenDependencyCache();

    /**
     * Get direct dependants of a specific document (for safe-delete).
     */
    Set<DocRef> getDependantsOf(DocRef docRef);
}
