package stroom.data.store.impl.fs;

import stroom.data.store.impl.fs.DataVolumeDao.DataVolume;
import stroom.meta.shared.Meta;
import stroom.meta.shared.SimpleMeta;
import stroom.util.io.PathCreator;

import jakarta.inject.Inject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class FsFileFinder {

    private final FsPathHelper fileSystemStreamPathHelper;
    private final DataVolumeService dataVolumeService;
    private final PathCreator pathCreator;

    @Inject
    FsFileFinder(final FsPathHelper fileSystemStreamPathHelper,
                 final DataVolumeService dataVolumeService,
                 final PathCreator pathCreator) {
        this.fileSystemStreamPathHelper = fileSystemStreamPathHelper;
        this.dataVolumeService = dataVolumeService;
        this.pathCreator = pathCreator;
    }

    Optional<Path> findRootStreamFile(final SimpleMeta meta) {
        final DataVolume dataVolume = dataVolumeService.findDataVolume(meta.getId());
        if (dataVolume != null) {
            return findRootStreamFile(meta, dataVolume);
        } else {
            return Optional.empty();
        }
    }


    Optional<Path> findRootStreamFile(final SimpleMeta meta, final DataVolume dataVolume) {
        if (dataVolume != null && dataVolume.getVolume().getPath() != null) {
            final Path volumePath = pathCreator.toAppPath(dataVolume.getVolume().getPath());
            final Path rootFile = fileSystemStreamPathHelper.getRootPath(
                    volumePath,
                    meta,
                    meta.getTypeName());
            if (Files.isRegularFile(rootFile)) {
                return Optional.of(rootFile);
            } else {
                return Optional.empty();
            }
        } else {
            return Optional.empty();
        }
    }

    List<Path> findAllStreamFile(final Meta meta) {
        return findRootStreamFile(meta)
                .map(rootFile -> {
                    final List<Path> results = new ArrayList<>();
                    results.add(rootFile);
                    results.addAll(fileSystemStreamPathHelper.findAllDescendantStreamFileList(rootFile));
                    return results;
                })
                .orElse(Collections.emptyList());
    }
}
