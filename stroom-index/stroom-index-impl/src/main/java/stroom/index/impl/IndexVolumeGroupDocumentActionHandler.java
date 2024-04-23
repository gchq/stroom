package stroom.index.impl;

import stroom.docref.DocRef;
import stroom.docref.DocRefInfo;
import stroom.docstore.api.DocumentActionHandler;
import stroom.index.shared.IndexVolumeGroup;
import stroom.util.NullSafe;

import jakarta.inject.Inject;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class IndexVolumeGroupDocumentActionHandler implements DocumentActionHandler<IndexVolumeGroup> {

    private final IndexVolumeGroupService indexVolumeGroupService;

    @Inject
    public IndexVolumeGroupDocumentActionHandler(final IndexVolumeGroupService indexVolumeGroupService) {
        this.indexVolumeGroupService = indexVolumeGroupService;
    }

    @Override
    public Set<DocRef> listDocuments() {
        return NullSafe.stream(indexVolumeGroupService.getAll())
                .map(IndexVolumeGroup::asDocRef)
                .collect(Collectors.toSet());
    }

    @Override
    public List<DocRef> findByNames(final List<String> names, final boolean allowWildCards) {
        return NullSafe.stream(indexVolumeGroupService.find(names, allowWildCards))
                .map(IndexVolumeGroup::asDocRef)
                .toList();
    }

    @Override
    public IndexVolumeGroup readDocument(final DocRef docRef) {
        return indexVolumeGroupService.get(docRef);
    }

    @Override
    public IndexVolumeGroup writeDocument(final IndexVolumeGroup document) {
        return null;
    }

    @Override
    public String getType() {
        return null;
    }

    @Override
    public DocRefInfo info(final String uuid) {
        return null;
    }
}
