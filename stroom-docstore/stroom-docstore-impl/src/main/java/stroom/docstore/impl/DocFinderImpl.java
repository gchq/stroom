/*
 * Copyright 2026 Crown Copyright
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
import stroom.docstore.api.DocFinder;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermission;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

class DocFinderImpl implements DocFinder {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DocFinderImpl.class);

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
        List<DocRef> docRefs;
        if (type == null) {
            // When type is null, search all document types as documented.
            // The name cache requires a non-null type key, so bypass it and go direct to persistence,
            // which treats a null/empty type collection as "all types".
            docRefs = persistence.find((Collection<String>) null, List.of(nameFilter), allowWildCards).stream()
                    .toList();
        } else {
            // Use the name cache for exact (non-wildcard) lookups.
            if (allowWildCards) {
                // Wildcard lookups go direct to Persistence, then filter.
                docRefs = persistence.find(type, nameFilter, true);
            } else {
                // Cache returns unfiltered results; apply permission filter at this boundary.
                docRefs = docRefFromNameCache.get(type, nameFilter);
            }
        }
        // Only return the docs the user has the perms to see
        docRefs = docRefs.stream()
                .filter(this::canUse)
                .toList();

        LOGGER.trace("findByNames() - type: {}, nameFilter: {}, allowWildCards: {}, docRefs: {}",
                type, nameFilter, allowWildCards, docRefs);
        return docRefs;
    }

    @Override
    public List<DocRef> findByNames(final String type,
                                    final List<String> nameFilters,
                                    final boolean allowWildCards) {
        final List<DocRef> docRefs;
        if (NullSafe.isEmptyCollection(nameFilters)) {
            docRefs = Collections.emptyList();
        } else if (nameFilters.size() == 1) {
            docRefs = findByName(type, nameFilters.getFirst(), allowWildCards);
        } else {
            // Multi name lookups use DB directly
            docRefs = persistence.find(type, nameFilters, allowWildCards);
        }
        LOGGER.trace("findByNames() - type: {}, nameFilters: {}, allowWildCards: {}, docRefs: {}",
                type, nameFilters, allowWildCards, docRefs);
        return docRefs;
    }

    @Override
    public Optional<String> getName(final DocRef docRef) {
        final Optional<String> optName = canUse(docRef)
                ? docRefToNameCache.getName(docRef)
                : Optional.empty();
        LOGGER.trace("getName() - docRef: {}, optName: {}", docRef, optName);
        return optName;
    }

    private boolean canUse(final DocRef docRef) {
        // This service only returns docrefs, not the docs themselves. User should only need
        // USE to see the name of a docref
        return securityContext.hasDocumentPermission(docRef, DocumentPermission.USE);
    }
}
