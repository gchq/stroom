package stroom.explorer.impl;

import stroom.docref.DocRef;
import stroom.docref.DocRefInfo;
import stroom.docref.HasFindDocsByName;
import stroom.docrefinfo.api.DocRefInfoService;
import stroom.docstore.api.DocumentActionHandlers;
import stroom.explorer.api.ExplorerActionHandler;
import stroom.feed.shared.FeedDoc;
import stroom.security.api.SecurityContext;
import stroom.util.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

class DocRefInfoServiceImpl implements DocRefInfoService {

    private final DocRefInfoCache docRefInfoCache;
    private final SecurityContext securityContext;
    private final Provider<DocumentActionHandlers> documentActionHandlersProvider;
    private final Provider<ExplorerActionHandlers> explorerActionHandlersProvider;

    @Inject
    DocRefInfoServiceImpl(final DocRefInfoCache docRefInfoCache,
                          final SecurityContext securityContext,
                          final Provider<DocumentActionHandlers> documentActionHandlersProvider,
                          final Provider<ExplorerActionHandlers> explorerActionHandlersProvider) {
        this.docRefInfoCache = docRefInfoCache;
        this.securityContext = securityContext;
        this.documentActionHandlersProvider = documentActionHandlersProvider;
        this.explorerActionHandlersProvider = explorerActionHandlersProvider;
    }

    @Override
    public List<DocRef> findByType(final String type) {
        Objects.requireNonNull(type);
        return securityContext.asProcessingUserResult(() -> {
            final ExplorerActionHandler handler = explorerActionHandlersProvider.get().getHandler(type);
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
        return docRefInfoCache.get(uuid);
    }

    @Override
    public Optional<String> name(final DocRef docRef) {
        return info(docRef)
                .map(DocRefInfo::getDocRef)
                .map(DocRef::getName);
    }

    @Override
    public Optional<String> name(final String uuid) {
        return info(uuid)
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
            return securityContext.asProcessingUserResult(() -> {
                if (type == null) {
                    // No type so have to search all handlers
                    final List<DocRef> result = new ArrayList<>();
                    final Set<String> typesChecked = new HashSet<>();

                    // Some types are not documents and some are not explorer types so
                    // check both
                    documentActionHandlersProvider.get().forEach(handler -> {
                        result.addAll(handler.findByName(nameFilter, allowWildCards));
                        typesChecked.add(handler.getType());
                    });

                    explorerActionHandlersProvider.get().forEach((handlerType, handler) -> {
                        if (!typesChecked.contains(handler.getDocumentType().getType())) {
                            result.addAll(handler.findByName(nameFilter, allowWildCards));
                        }
                    });
                    return result;
                } else {
                    HasFindDocsByName handler = null;
                    handler = documentActionHandlersProvider.get().getHandler(type);
                    if (handler == null) {
                        handler = explorerActionHandlersProvider.get().getHandler(type);
                    }

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
            return securityContext.asProcessingUserResult(() -> {
                final ExplorerActionHandler handler = explorerActionHandlersProvider.get().getHandler(type);
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
                    .map(docRef -> decorate(docRef, false))
                    .collect(Collectors.toList());
        }
    }

    @Override
    public DocRef decorate(final DocRef docRef, final boolean force) {
        Objects.requireNonNull(docRef);

        // Allow decorate by name alone if feed (special case).
        if (FeedDoc.DOCUMENT_TYPE.equals(docRef.getType()) && docRef.getUuid() == null) {
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
                    .orElseThrow(() -> new RuntimeException("No docRefInfo for docRef: " + docRef));
        } else {
            return docRef;
        }
    }
}
