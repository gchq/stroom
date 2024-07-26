package stroom.docrefinfo.mock;

import stroom.docref.DocRef;
import stroom.docref.DocRefInfo;
import stroom.docrefinfo.api.DocRefInfoService;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class MockDocRefInfoService implements DocRefInfoService {

    @Override
    public Optional<DocRefInfo> info(final DocRef docRef) {
        return Optional.of(DocRefInfo.builder()
                .docRef(docRef)
                .build());
    }

    @Override
    public Optional<DocRefInfo> info(final String uuid) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Optional<String> name(final DocRef docRef) {
        return Optional.ofNullable(docRef.getName());
    }

    @Override
    public Optional<String> name(final String uuid) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<DocRef> findByName(final String type,
                                   final String nameFilter,
                                   final boolean allowWildCards,
                                   final boolean isCaseSensitive) {
        return Collections.emptyList();
    }

    @Override
    public List<DocRef> findByNames(final String type,
                                    final List<String> nameFilters,
                                    final boolean allowWildCards,
                                    final boolean isCaseSensitive) {
        return Collections.emptyList();
    }

    @Override
    public List<DocRef> findByType(final String type) {
        return Collections.emptyList();
    }

    @Override
    public DocRef decorate(final DocRef docRef, final boolean force) {
        return docRef;
    }

    @Override
    public List<DocRef> decorate(final List<DocRef> docRefs) {
        return docRefs;
    }
}
