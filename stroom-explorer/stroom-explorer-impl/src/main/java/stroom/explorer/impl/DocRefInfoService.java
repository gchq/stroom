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

package stroom.explorer.impl;

import stroom.docref.DocAuditEntry;
import stroom.docref.DocRef;
import stroom.docstore.api.DocFinder;
import stroom.docstore.api.DocumentNotFoundException;
import stroom.explorer.api.ExplorerService;
import stroom.explorer.api.IsSpecialExplorerDataSource;
import stroom.explorer.shared.ExplorerConstants;
import stroom.explorer.shared.ExplorerNode;
import stroom.feed.shared.FeedDoc;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermission;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PermissionException;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Singleton
class DocRefInfoService {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DocRefInfoService.class);

    private final Provider<SecurityContext> securityContextProvider;
    private final Provider<ExplorerService> explorerServiceProvider;
    private final Set<IsSpecialExplorerDataSource> specialExplorerDataSources;
    private final DocFinder docFinder;

    // Cache the set of special (non-DB) data source type names.
    private volatile Set<String> specialTypes = null;

    @Inject
    DocRefInfoService(final Provider<SecurityContext> securityContextProvider,
                      final Provider<ExplorerService> explorerServiceProvider,
                      final Set<IsSpecialExplorerDataSource> specialExplorerDataSources,
                      final DocFinder docFinder) {
        this.securityContextProvider = securityContextProvider;
        this.explorerServiceProvider = explorerServiceProvider;
        this.specialExplorerDataSources = specialExplorerDataSources;
        this.docFinder = docFinder;
    }

    public ResultPage<DocAuditEntry> getAuditInfo(final DocRef docRef) {
        return docFinder.getAuditInfo(docRef);
    }

    public Optional<DocRef> decorate(final DocRef docRef) {
        return decorate(docRef, null);
    }

    public Optional<DocRef> decorate(final DocRef docRef,
                                     final Set<DocumentPermission> requiredPermissions) {
        try {
            final SecurityContext securityContext = securityContextProvider.get();
            Objects.requireNonNull(docRef);

            // Allows the caller to do a perm check at the same time as decorating the docRef
            NullSafe.forEach(requiredPermissions, permName -> {
                if (!securityContext.hasDocumentPermission(docRef, permName)) {
                    throw new PermissionException(
                            securityContext.getUserRef(),
                            "You do not have permission to decorate this "
                            + Objects.requireNonNullElse(docRef.getType(), "document"));
                }
            });

            // Special case for System that isn't in the db.
            if (ExplorerConstants.SYSTEM_DOC_REF.equals(docRef)) {
                return Optional.of(ExplorerConstants.SYSTEM_DOC_REF);
            }

            // Special case for folders as they aren't actual documents.
            if (ExplorerConstants.FOLDER_TYPE.equals(docRef.getType())) {
                final ExplorerNode explorerNode = explorerServiceProvider.get().getFromDocRef(docRef)
                        .orElseThrow(() -> new RuntimeException("No explorerNode for " + docRef));
                return Optional.of(explorerNode.getDocRef());
            }

            // Treat special docs (Processor Tasks etc) differently.
            if (isSpecialDocRefType(docRef.getType())) {
                return streamSpecialDocRefs()
                        .filter(docRef::equals)
                        .findAny();
            }

            // Allow decoration by name alone if feed (special case).
            if (FeedDoc.TYPE.equals(docRef.getType()) && docRef.getUuid() == null) {
                final List<DocRef> list = docFinder.findByName(docRef.getType(), docRef.getName());
                if (!NullSafe.isEmptyCollection(list)) {
                    return Optional.of(list.getFirst());
                } else {
                    return Optional.empty();
                }
            }

            // The passed docRef may have all the parts, but it may be from before a rename, so if force
            // is set, use the cached copy which should be up-to-date.
            return Optional.of(docFinder.decorateIfExists(docRef).orElseThrow(() ->
                    new DocumentNotFoundException(docRef)));
        } catch (final Exception e) {
            LOGGER.debug(e::getMessage, e);
        }
        return Optional.empty();
    }

    /**
     * Get the set of special (non-DB) data source type names. Built once as processing user
     * so that all types are discovered regardless of the calling user's permissions.
     */
    private Set<String> getSpecialTypes() {
        if (specialTypes == null) {
            specialTypes = securityContextProvider.get().asProcessingUserResult(() ->
                    NullSafe.stream(specialExplorerDataSources)
                            .map(IsSpecialExplorerDataSource::getDataSourceDocRefs)
                            .flatMap(NullSafe::stream)
                            .map(DocRef::getType)
                            .collect(Collectors.toSet()));
        }
        return specialTypes;
    }

    private boolean isSpecialDocRefType(final String type) {
        return NullSafe.isNonEmptyString(type) && getSpecialTypes().contains(type);
    }

    private Stream<DocRef> streamSpecialDocRefs() {
        return NullSafe.stream(specialExplorerDataSources)
                .map(IsSpecialExplorerDataSource::getDataSourceDocRefs)
                .flatMap(NullSafe::stream);
    }
}
