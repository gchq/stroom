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

import stroom.docref.DocRef;
import stroom.docref.DocRefInfo;
import stroom.docrefinfo.api.DocRefInfoService;
import stroom.docstore.api.DocumentNotFoundException;
import stroom.explorer.api.ExplorerActionHandler;
import stroom.explorer.api.IsSpecialExplorerDataSource;
import stroom.explorer.shared.ExplorerConstants;
import stroom.feed.shared.FeedDoc;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PermissionException;
import stroom.util.string.PatternUtil;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Singleton
class DocRefInfoServiceImpl implements DocRefInfoService {

    private final DocRefInfoCache docRefInfoCache;
    private final Provider<SecurityContext> securityContextProvider;
    private final ExplorerActionHandlers explorerActionHandlers;
    private final Set<IsSpecialExplorerDataSource> specialExplorerDataSources;

    // Cache these special types keyed by DocRef type.
    private volatile Map<String, Set<DocRef>> specialDocRefsByType = null;

    @Inject
    DocRefInfoServiceImpl(final DocRefInfoCache docRefInfoCache,
                          final Provider<SecurityContext> securityContextProvider,
                          final ExplorerActionHandlers explorerActionHandlers,
                          final Set<IsSpecialExplorerDataSource> specialExplorerDataSources) {
        this.docRefInfoCache = docRefInfoCache;
        this.securityContextProvider = securityContextProvider;
        this.explorerActionHandlers = explorerActionHandlers;
        this.specialExplorerDataSources = specialExplorerDataSources;
    }

    @Override
    public List<DocRef> findByType(final String type) {
        Objects.requireNonNull(type);
        return securityContextProvider.get().asProcessingUserResult(() -> {
            final ExplorerActionHandler handler = explorerActionHandlers.getHandler(type);
            if (handler != null) {
                return NullSafe.stream(handler.listDocuments())
                        .toList();
            } else {
                if (isSpecialDocRefType(type)) {
                    final Set<DocRef> specialDocRefs = getSpecialDocRefs(type);
                    return NullSafe.stream(specialDocRefs).toList();
                } else {
                    throw new RuntimeException("No handler for type " + type);
                }
            }
        });
    }

    @Override
    public Optional<DocRefInfo> info(final DocRef docRef) {
        return docRefInfoCache.get(docRef)
                .or(() -> getSpecialDocRefInfo(docRef));
    }

    @Override
    public Optional<DocRefInfo> info(final String uuid) {
        final DocRef docRef = DocRef.builder()
                .type(DocRefInfoCache.UNKNOWN_TYPE)
                .uuid(uuid)
                .build();

        return docRefInfoCache.get(docRef)
                .or(() -> {
                    // No type so have to loop through all searchable providers
                    return getSpecialDocRefsByType().values()
                            .stream()
                            .flatMap(Collection::stream)
                            .filter(docRef::equals)
                            .map(aDocRef -> DocRefInfo.builder()
                                    .docRef(aDocRef)
                                    .build())
                            .findAny();
                });
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

                    final Predicate<DocRef> predicate = PatternUtil.createPredicate(
                            List.of(nameFilter),
                            DocRef::getName,
                            allowWildCards,
                            true,
                            true);

                    getSpecialDocRefsByType().values()
                            .stream()
                            .flatMap(Collection::stream)
                            .filter(predicate)
                            .forEach(result::add);
                    return result;
                } else {
                    final ExplorerActionHandler handler = explorerActionHandlers.getHandler(type);
                    if (handler != null) {
                        return handler.findByName(nameFilter, allowWildCards);
                    } else {
                        final Set<DocRef> specialDocRefs = getSpecialDocRefs(type);
                        if (specialDocRefs != null) {
                            final Predicate<DocRef> predicate = PatternUtil.createPredicate(
                                    List.of(nameFilter),
                                    DocRef::getName,
                                    allowWildCards,
                                    true,
                                    true);
                            return specialDocRefs.stream()
                                    .filter(predicate)
                                    .collect(Collectors.toList());
                        } else {
                            throw new RuntimeException("No handler for type " + type);
                        }
                    }
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
                if (handler != null) {
                    return handler.findByNames(nameFilters, allowWildCards);
                } else {
                    final Set<DocRef> specialDocRefs = getSpecialDocRefs(type);
                    if (specialDocRefs != null) {
                        final Predicate<DocRef> predicate = PatternUtil.createPredicate(
                                nameFilters,
                                DocRef::getName,
                                allowWildCards,
                                true,
                                true);
                        return specialDocRefs.stream()
                                .filter(predicate)
                                .collect(Collectors.toList());
                    } else {
                        throw new RuntimeException("No handler for type " + type);
                    }
                }
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
                    .or(() ->
                            NullSafe.stream(getSpecialDocRefs(docRef.getType()))
                                    .filter(docRef::equals)
                                    .findAny())
                    .orElseThrow(() ->
                            new DocumentNotFoundException(docRef));
        } else {
            return docRef;
        }
    }

    private Map<String, Set<DocRef>> getSpecialDocRefsByType() {
        if (specialDocRefsByType == null) {
            specialDocRefsByType = NullSafe.stream(specialExplorerDataSources)
                    .map(IsSpecialExplorerDataSource::getDataSourceDocRefs)
                    .flatMap(NullSafe::stream)
                    .collect(Collectors.groupingBy(DocRef::getType, Collectors.toSet()));
        }
        return specialDocRefsByType;
    }

    private Set<DocRef> getSpecialDocRefs(final String type) {
        if (NullSafe.isNonEmptyString(type)) {
            return getSpecialDocRefsByType().get(type);
        } else {
            return Collections.emptySet();
        }
    }

    private boolean isSpecialDocRefType(final String type) {
        if (NullSafe.isNonEmptyString(type)) {
            return getSpecialDocRefsByType().containsKey(type);
        } else {
            return false;
        }
    }

    private Optional<DocRefInfo> getSpecialDocRefInfo(final DocRef docRef) {
        if (docRef == null) {
            return Optional.empty();
        } else {
            return NullSafe.stream(getSpecialDocRefs(docRef.getType()))
                    .filter(docRef::equals)
                    .map(aDocRef -> DocRefInfo.builder()
                            .docRef(aDocRef)
                            .build())
                    .findAny();
        }
    }
}
