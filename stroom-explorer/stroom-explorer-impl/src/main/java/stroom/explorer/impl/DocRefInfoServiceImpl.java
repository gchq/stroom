/*
 * Copyright 2024 Crown Copyright
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

import stroom.docref.DocRef;
import stroom.docref.DocRefInfo;
import stroom.docrefinfo.api.DocRefInfoService;
import stroom.docstore.api.DocumentNotFoundException;
import stroom.explorer.api.ExplorerActionHandler;
import stroom.explorer.shared.ExplorerConstants;
import stroom.feed.shared.FeedDoc;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PermissionException;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

class DocRefInfoServiceImpl implements DocRefInfoService {

    private final DocRefInfoCache docRefInfoCache;
    private final Provider<SecurityContext> securityContextProvider;
    private final ExplorerActionHandlers explorerActionHandlers;

    @Inject
    DocRefInfoServiceImpl(final DocRefInfoCache docRefInfoCache,
                          final Provider<SecurityContext> securityContextProvider,
                          final ExplorerActionHandlers explorerActionHandlers) {
        this.docRefInfoCache = docRefInfoCache;
        this.securityContextProvider = securityContextProvider;
        this.explorerActionHandlers = explorerActionHandlers;
    }

    @Override
    public List<DocRef> findByType(final String type) {
        Objects.requireNonNull(type);
        return securityContextProvider.get().asProcessingUserResult(() -> {
            final ExplorerActionHandler handler = explorerActionHandlers.getHandler(type);
            Objects.requireNonNull(handler, () -> "No handler for type " + type);
            return new ArrayList<>(handler.listDocuments());
        });
    }

    @Override
    public Optional<DocRefInfo> info(final DocRef docRef) {
        return docRefInfoCache.get(docRef);
    }

    @Override
    public Optional<DocRefInfo> info(final String uuid) {
        return docRefInfoCache.get(DocRef.builder().type(DocRefInfoCache.UNKNOWN_TYPE).uuid(uuid).build());
    }

    @Override
    public Optional<String> name(final DocRef docRef) {
        return info(docRef)
                .map(DocRefInfo::getDocRef)
                .map(DocRef::getName);
    }

    @Override
    public List<DocRef> findByName(final String type,
                                   final String nameFilter,
                                   final boolean allowWildCards) {
        if (NullSafe.isEmptyString(nameFilter)) {
            return Collections.emptyList();
        } else {
            return securityContextProvider.get().asProcessingUserResult(() -> {
                if (type == null) {
                    // No type so have to search all handlers
                    final List<DocRef> result = new ArrayList<>();
                    explorerActionHandlers.forEach((handlerType, handler) ->
                            result.addAll(handler.findByName(nameFilter, allowWildCards)));
                    return result;
                } else {
                    final ExplorerActionHandler handler = explorerActionHandlers.getHandler(type);
                    Objects.requireNonNull(handler, () -> "No handler for type " + type);
                    return handler.findByName(nameFilter, allowWildCards);
                }
            });
        }
    }

    @Override
    public List<DocRef> findByNames(final String type,
                                    final List<String> nameFilters,
                                    final boolean allowWildCards) {
        Objects.requireNonNull(type);
        if (NullSafe.isEmptyCollection(nameFilters)) {
            return Collections.emptyList();
        } else {
            return securityContextProvider.get().asProcessingUserResult(() -> {
                final ExplorerActionHandler handler = explorerActionHandlers.getHandler(type);
                Objects.requireNonNull(handler, () -> "No handler for type " + type);
                return handler.findByNames(nameFilters, allowWildCards);
            });
        }
    }

    @Override
    public List<DocRef> decorate(final List<DocRef> docRefs) {
        if (NullSafe.isEmptyCollection(docRefs)) {
            return Collections.emptyList();
        } else {
            return docRefs.stream()
                    .filter(Objects::nonNull)
                    .map(docRef ->
                            decorate(docRef, false, null))
                    .collect(Collectors.toList());
        }
    }

    @Override
    public DocRef decorate(final DocRef docRef,
                           final boolean force) {
        return decorate(docRef, force, null);
    }

    @Override
    public DocRef decorate(final DocRef docRef,
                           final boolean force,
                           final Set<DocumentPermission> requiredPermissions) {
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
            return ExplorerConstants.SYSTEM_DOC_REF;
        }

        // Allow decoration by name alone if feed (special case).
        if (FeedDoc.TYPE.equals(docRef.getType()) && docRef.getUuid() == null) {
            final List<DocRef> list = findByName(docRef.getType(), docRef.getName(), false);
            if (!NullSafe.isEmptyCollection(list)) {
                return list.getFirst();
            } else {
                return null;
            }
        }

        Objects.requireNonNull(docRef.getUuid(), "DocRef UUID is not set.");

        // The passed docRef may have all the parts, but it may be from before a rename, so if force
        // is set, use the cached copy which should be up-to-date.
        if (NullSafe.isEmptyString(docRef.getType())
            || NullSafe.isEmptyString(docRef.getName())
            || force) {
            return docRefInfoCache.get(docRef)
                    .map(DocRefInfo::getDocRef)
                    .orElseThrow(() ->
                            new DocumentNotFoundException(docRef));
        } else {
            return docRef;
        }
    }

}
