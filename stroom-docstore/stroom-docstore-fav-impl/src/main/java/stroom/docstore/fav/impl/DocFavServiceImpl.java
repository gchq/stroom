package stroom.docstore.fav.impl;

import stroom.docref.DocRef;
import stroom.docrefinfo.api.DocRefInfoService;
import stroom.docstore.fav.api.DocFavService;
import stroom.explorer.api.ExplorerService;
import stroom.security.api.SecurityContext;

import java.util.List;
import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Provider;

public class DocFavServiceImpl implements DocFavService {

    private final DocFavDao docFavDao;
    private final SecurityContext securityContext;
    private final Provider<DocRefInfoService> docRefInfoService;
    private final Provider<ExplorerService> explorerService;

    @Inject
    DocFavServiceImpl(final DocFavDao docFavDao,
                      final SecurityContext securityContext,
                      final Provider<DocRefInfoService> docRefInfoService,
                      final Provider<ExplorerService> explorerService) {
        this.docFavDao = docFavDao;
        this.securityContext = securityContext;
        this.docRefInfoService = docRefInfoService;
        this.explorerService = explorerService;
    }

    @Override
    public void create(final DocRef docRef) {
        final String userId = securityContext.getUserUuid();
        Objects.requireNonNull(userId);
        docFavDao.setDocFavForUser(docRef, userId);
        explorerService.get().rebuildTree();
    }

    @Override
    public void delete(final DocRef docRef) {
        final String userId = securityContext.getUserUuid();
        Objects.requireNonNull(userId);
        docFavDao.deleteDocFavForUser(docRef, userId);
        explorerService.get().rebuildTree();
    }

    @Override
    public List<DocRef> fetchDocFavs() {
        final String userId = securityContext.getUserUuid();
        Objects.requireNonNull(userId);
        return docFavDao.getUserDocFavs(userId)
                .stream()
                .map(docRef -> docRefInfoService.get().decorate(docRef))
                .toList();
    }

    @Override
    public boolean isDocFav(final DocRef docRef) {
        final String userId = securityContext.getUserUuid();
        Objects.requireNonNull(userId);
        return docFavDao.isDocFav(docRef, userId);
    }
}
