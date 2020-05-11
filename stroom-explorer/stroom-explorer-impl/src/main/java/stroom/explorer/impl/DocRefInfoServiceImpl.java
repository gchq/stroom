package stroom.explorer.impl;

import stroom.docref.DocRef;
import stroom.docref.DocRefInfo;
import stroom.docrefinfo.api.DocRefInfoService;

import javax.inject.Inject;
import java.util.Optional;

class DocRefInfoServiceImpl implements DocRefInfoService {
    private final DocRefInfoCache docRefInfoCache;

    @Inject
    DocRefInfoServiceImpl(final DocRefInfoCache docRefInfoCache) {
        this.docRefInfoCache = docRefInfoCache;
    }

    @Override
    public Optional<DocRefInfo> info(final DocRef docRef) {
        return docRefInfoCache.get(docRef);
    }

    @Override
    public Optional<String> name(final DocRef docRef) {
        return info(docRef)
                .map(DocRefInfo::getDocRef)
                .map(DocRef::getName);
    }
}
