package stroom.data.store.impl.fs;

import stroom.data.store.api.FsVolumeGroupService;
import stroom.data.store.impl.fs.shared.FsVolumeGroup;
import stroom.docref.DocRef;
import stroom.docstore.api.DocumentNotFoundException;
import stroom.docstore.api.Persistence;
import stroom.docstore.api.RWLockFactory;
import stroom.util.NullSafe;

import jakarta.inject.Inject;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

// TODO delete, probably
public class FsVolumeGroupPersistence implements Persistence {

    private final FsVolumeGroupService fsVolumeGroupService;

    @Inject
    public FsVolumeGroupPersistence(final FsVolumeGroupService fsVolumeGroupService) {
        this.fsVolumeGroupService = fsVolumeGroupService;
    }

    @Override
    public boolean exists(final DocRef docRef) {
        Objects.requireNonNull(docRef);
        return fsVolumeGroupService.get(docRef) != null;
    }

    @Override
    public void delete(final DocRef docRef) {
        Objects.requireNonNull(docRef);
        final FsVolumeGroup volumeGroup = fsVolumeGroupService.get(docRef);

        if (volumeGroup == null) {
            throw new DocumentNotFoundException(docRef);
        }
        fsVolumeGroupService.delete(volumeGroup.getId());
    }

    @Override
    public Map<String, byte[]> read(final DocRef docRef) throws IOException {
        return null;
    }

    @Override
    public void write(final DocRef docRef, final boolean update, final Map<String, byte[]> data) throws IOException {

    }

    @Override
    public List<DocRef> list(final String type) {
        return NullSafe.stream(fsVolumeGroupService.getAll())
                .map(FsVolumeGroup::asDocRef)
                .toList();
    }

    @Override
    public RWLockFactory getLockFactory() {
        // No locks needed
        return new RWLockFactory() {
            @Override
            public void lock(final String uuid, final Runnable runnable) {
                runnable.run();
            }

            @Override
            public <T> T lockResult(final String uuid, final Supplier<T> supplier) {
                return supplier.get();
            }
        };
    }
}
