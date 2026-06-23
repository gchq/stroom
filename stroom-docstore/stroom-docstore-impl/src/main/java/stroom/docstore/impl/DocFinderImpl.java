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

package stroom.docstore.impl;

import stroom.docref.DocAuditEntry;
import stroom.docref.DocRef;
import stroom.docstore.api.DocFinder;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

class DocFinderImpl implements DocFinder {

    private final DocRefFromNameCache docRefFromNameCache;
    private final DocRefToNameCache docRefToNameCache;
    private final Persistence persistence;
    private final SecurityContext securityContext;

    @Inject
    public DocFinderImpl(final DocRefFromNameCache docRefFromNameCache,
                         final DocRefToNameCache docRefToNameCache,
                         final Persistence persistence,
                         final SecurityContext securityContext) {
        this.docRefFromNameCache = docRefFromNameCache;
        this.docRefToNameCache = docRefToNameCache;
        this.persistence = persistence;
        this.securityContext = securityContext;
    }

    @Override
    public List<DocRef> findByName(final String type,
                                   final String nameFilter,
                                   final boolean allowWildCards) {
        // Use the name cache for exact (non-wildcard) lookups.
        if (!allowWildCards) {
            // Cache returns unfiltered results; apply permission filter at this boundary.
            return docRefFromNameCache.get(type, nameFilter).stream()
                    .filter(this::canView)
                    .collect(Collectors.toList());
        }

        // Wildcard lookups go direct to Persistence, then filter.
        return persistence.find(type, nameFilter, true).stream()
                .filter(this::canView)
                .collect(Collectors.toList());
    }

    @Override
    public List<DocRef> findByNames(final String type,
                                    final List<String> nameFilters,
                                    final boolean allowWildCards) {
        if (NullSafe.isEmptyCollection(nameFilters)) {
            return Collections.emptyList();
        }
        if (nameFilters.size() == 1) {
            return findByName(type, nameFilters.getFirst(), allowWildCards);
        }

        // Multi name lookups use DB directly
        return persistence.find(type, nameFilters, allowWildCards);
    }

    @Override
    public Optional<String> getName(final DocRef docRef) {
        if (canView(docRef)) {
            return docRefToNameCache.getName(docRef);
        }
        return Optional.empty();
    }

    @Override
    public ResultPage<DocAuditEntry> getAuditInfo(final DocRef docRef) {
        if (canView(docRef)) {
            return persistence.getAuditInfo(docRef);
        }
        return ResultPage.empty();
    }

    private boolean canView(final DocRef docRef) {
        return securityContext.hasDocumentPermission(docRef, DocumentPermission.VIEW);
    }
}
