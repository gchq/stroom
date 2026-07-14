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
 * <p>
 * Dependencies are persisted in the doc_dependency table and kept current by direct calls from the
 * write paths of the entities that own them: as an entity is saved its owner (e.g. the docstore
 * {@link Store}, or the processor filter service) extracts the entity's dependencies from the object
 * it has just persisted and stores them via {@link #setDependencies}. Deletes clear edges via
 * {@link #removeDependencies}, and renames propagate the new name via {@link #propagateName}.
 * <p>
 */
public interface DocDependencyService {

    /**
     * Replace all stored edges for a document (or other entity, e.g. a processor filter) with the
     * supplied, already-computed dependencies. Callers extract the dependencies directly from the
     * object they have just persisted, so no re-read of the entity is required.
     */
    void setDependencies(DocRef docRef, Set<DocRef> dependencies);

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
     * Get all broken dependencies (edges whose target no longer exists in the doc table), keyed by
     * the source document. Any source that is not itself an explorer tree node (e.g. a ProcessorFilter)
     * is rolled up to its owning explorer document (e.g. the filter's pipeline) so the broken
     * dependency can be surfaced on a real tree node.
     * <p>
     * Served from a short-lived cache that is refreshed from the DB on a time-based expiry, so results
     * may lag a recent change by a few seconds. Call {@link #invalidateBrokenDependencyCache()} to
     * force the next call to rebuild from the DB.
     *
     * @param pseudoRefUuids UUIDs of known pseudo-refs (e.g. Searchable / Annotation refs) that live
     *                       outside the doc table and so must not be treated as broken targets.
     */
    Map<DocRef, Set<DocRef>> fetchBrokenDependencies(final Set<String> pseudoRefUuids);

    /**
     * Invalidate the broken-dependency cache so the next call to
     * {@link #fetchBrokenDependencies(Set)} rebuilds it from the DB rather than serving a stale
     * (time-expired) snapshot. Useful after a change that may have broken or fixed a dependency when
     * the result needs to be reflected immediately (e.g. an explorer tree rebuild).
     */
    void invalidateBrokenDependencyCache();

    /**
     * Get direct dependants of a specific document (for safe-delete).
     */
    Set<DocRef> getDependantsOf(DocRef docRef);
}
