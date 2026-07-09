/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.docstore.impl.dao;

import stroom.docref.DocRef;
import stroom.docstore.api.DocDependencyService;
import stroom.importexport.shared.Dependency;
import stroom.importexport.shared.DependencyCriteria;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermission;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

@Singleton
public class DocDependencyServiceImpl implements DocDependencyService {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DocDependencyServiceImpl.class);

    private final DocDependencyDao docDependencyDao;
    private final BrokenDependenciesCache brokenDependenciesCache;
    private final SecurityContext securityContext;

    @Inject
    DocDependencyServiceImpl(final DocDependencyDao docDependencyDao,
                             final BrokenDependenciesCache brokenDependenciesCache,
                             final SecurityContext securityContext) {
        this.docDependencyDao = docDependencyDao;
        this.brokenDependenciesCache = brokenDependenciesCache;
        this.securityContext = securityContext;
    }

    @Override
    public void setDependencies(final DocRef docRef, final Set<DocRef> dependencies) {
        try {
            // The DAO tolerates a null/empty set (it just clears existing edges).
            docDependencyDao.setDependencies(docRef, dependencies);
        } catch (final RuntimeException e) {
            LOGGER.error(() -> "Error setting dependencies for " + docRef + ": " + e.getMessage(), e);
        }
    }

    @Override
    public void removeDependencies(final DocRef docRef) {
        try {
            docDependencyDao.deleteAllForDoc(docRef.getUuid());
        } catch (final RuntimeException e) {
            LOGGER.error(() -> "Error removing dependencies for " + docRef + ": " + e.getMessage(), e);
        }
    }

    @Override
    public void propagateName(final DocRef docRef) {
        try {
            docDependencyDao.updateName(docRef.getUuid(), docRef.getName());
        } catch (final RuntimeException e) {
            LOGGER.error(() -> "Error propagating name for " + docRef + ": " + e.getMessage(), e);
        }
    }

    @Override
    public ResultPage<Dependency> fetchDependencies(final DependencyCriteria criteria) {
        // Build a permission filter predicate.
        // Admin users see everything; non-admins only see dependency edges
        // where BOTH the from and to docs are visible to them.
        final Predicate<Dependency> filter = securityContext.isAdmin()
                ? dep -> true
                : dep ->
                        hasViewPermission(dep.getFrom()) && (dep.getTo() == null || hasViewPermission(dep.getTo()));

        // Fetch from the doc_dependency table. The predicate is applied
        // during streaming before pagination, so totals are correct.
        return docDependencyDao.fetchDependencies(criteria, filter);
    }

    @Override
    public Map<DocRef, Set<DocRef>> fetchBrokenDependencies(final Set<String> pseudoRefUuids) {
        // The cache (via the DAO) already rolls up any non-explorer source (e.g. a ProcessorFilter)
        // to its owning explorer document, so broken deps are keyed on real tree nodes.
        return brokenDependenciesCache.getMap(pseudoRefUuids);
    }

    @Override
    public Set<DocRef> getDependantsOf(final DocRef docRef) {
        return docDependencyDao.getDependantsOf(docRef.getUuid());
    }

    private boolean hasViewPermission(final DocRef docRef) {
        if (docRef == null || docRef.getUuid() == null) {
            return true;
        }
        try {
            return securityContext.hasDocumentPermission(docRef, DocumentPermission.VIEW);
        } catch (final RuntimeException e) {
            // If permission check fails (e.g. doc type not recognised as a
            // real document), allow it through rather than hiding the edge.
            return true;
        }
    }

    @Override
    public void invalidateBrokenDependencyCache() {
        brokenDependenciesCache.invalidate();
    }
}
