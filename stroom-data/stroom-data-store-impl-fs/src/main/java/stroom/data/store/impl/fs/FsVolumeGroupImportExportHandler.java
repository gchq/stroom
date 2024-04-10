package stroom.data.store.impl.fs;

import stroom.data.store.impl.fs.shared.FsVolumeGroup;
import stroom.docref.DocRef;
import stroom.docref.DocRefInfo;
import stroom.docref.HasDocRef;
import stroom.importexport.api.ImportExportActionHandler;
import stroom.importexport.api.NonExplorerDocRefProvider;
import stroom.importexport.shared.ImportSettings;
import stroom.importexport.shared.ImportState;
import stroom.util.NullSafe;
import stroom.util.logging.LogUtil;
import stroom.util.shared.Message;

import jakarta.inject.Inject;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class FsVolumeGroupImportExportHandler
        implements ImportExportActionHandler, NonExplorerDocRefProvider {

    private final FsVolumeGroupService fsVolumeGroupService;

    @Inject
    public FsVolumeGroupImportExportHandler(final FsVolumeGroupService fsVolumeGroupService) {
        this.fsVolumeGroupService = fsVolumeGroupService;
    }

    @Override
    public DocRef importDocument(final DocRef docRef,
                                 final Map<String, byte[]> dataMap,
                                 final ImportState importState,
                                 final ImportSettings importSettings) {
        return null;
    }

    @Override
    public Map<String, byte[]> exportDocument(final DocRef docRef,
                                              final boolean omitAuditFields,
                                              final List<Message> messageList) {
        return null;
    }

    @Override
    public Set<DocRef> listDocuments() {
        return NullSafe.stream(fsVolumeGroupService.getAll())
                .map(HasDocRef::asDocRef)
                .collect(Collectors.toSet());
    }

    @Override
    public String getType() {
        return FsVolumeGroup.DOCUMENT_TYPE;
    }

    @Override
    public Set<DocRef> findAssociatedNonExplorerDocRefs(final DocRef docRef) {
        // Vol groups have no associations
        return Collections.emptySet();
    }

    @Override
    public DocRef getOwnerDocument(final DocRef docRef, final Map<String, byte[]> dataMap) {
        return null;
    }

    @Override
    public DocRef findNearestExplorerDocRef(final DocRef docref) {
        return null;
    }

    @Override
    public DocRefInfo info(final String uuid) {
        final DocRef docRef = FsVolumeGroup.buildDocRef()
                .uuid(uuid)
                .build();
        return Optional.ofNullable(fsVolumeGroupService.get(docRef))
                .map(fsVolumeGroup ->
                        DocRefInfo.builder()
                                .docRef(fsVolumeGroup.asDocRef())
                                .createTime(fsVolumeGroup.getCreateTimeMs())
                                .createUser(fsVolumeGroup.getCreateUser())
                                .updateTime(fsVolumeGroup.getUpdateTimeMs())
                                .updateUser(fsVolumeGroup.getUpdateUser())
                                .build())
                .orElseThrow(() -> new IllegalArgumentException(LogUtil.message(
                        "FS Volume Group with UUID {} not found", uuid)));
    }

    @Override
    public Map<DocRef, Set<DocRef>> getDependencies() {
        // No deps
        return Collections.emptyMap();
    }

    @Override
    public Set<DocRef> getDependencies(final DocRef docRef) {
        // No deps
        return Collections.emptySet();
    }

    @Override
    public void remapDependencies(final DocRef docRef, final Map<DocRef, DocRef> remappings) {
        // No deps
    }
}
