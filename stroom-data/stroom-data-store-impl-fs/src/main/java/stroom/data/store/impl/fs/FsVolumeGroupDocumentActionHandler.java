package stroom.data.store.impl.fs;

import stroom.data.store.api.FsVolumeGroupService;
import stroom.data.store.impl.fs.shared.FsVolumeGroup;
import stroom.docref.DocRef;
import stroom.docref.DocRefInfo;
import stroom.docstore.api.DocumentActionHandler;
import stroom.util.NullSafe;

import jakarta.inject.Inject;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class FsVolumeGroupDocumentActionHandler implements DocumentActionHandler<FsVolumeGroup> {

    private final FsVolumeGroupService fsVolumeGroupService;

    @Inject
    public FsVolumeGroupDocumentActionHandler(final FsVolumeGroupService fsVolumeGroupService) {
        this.fsVolumeGroupService = fsVolumeGroupService;
    }

    @Override
    public Set<DocRef> listDocuments() {
        return NullSafe.stream(fsVolumeGroupService.getAll())
                .map(FsVolumeGroup::asDocRef)
                .collect(Collectors.toSet());
    }

    @Override
    public List<DocRef> findByNames(final List<String> names, final boolean allowWildCards) {
        return NullSafe.stream(fsVolumeGroupService.find(names, allowWildCards))
                .map(FsVolumeGroup::asDocRef)
                .toList();
    }

    @Override
    public FsVolumeGroup readDocument(final DocRef docRef) {
        return fsVolumeGroupService.get(docRef);
    }

    @Override
    public FsVolumeGroup writeDocument(final FsVolumeGroup document) {
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
