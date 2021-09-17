package stroom.data.store.impl.fs;

import stroom.data.store.impl.fs.DataVolumeDao.DataVolume;
import stroom.meta.shared.Meta;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import javax.inject.Inject;

public class FsFileFinder {

    private final FsPathHelper fileSystemStreamPathHelper;
    private final DataVolumeService dataVolumeService;

    @Inject
    FsFileFinder(final FsPathHelper fileSystemStreamPathHelper,
                 final DataVolumeService dataVolumeService) {
        this.fileSystemStreamPathHelper = fileSystemStreamPathHelper;
        this.dataVolumeService = dataVolumeService;
    }

    Optional<Path> findRootStreamFile(final Meta meta) {
        final DataVolume dataVolume = dataVolumeService.findDataVolume(meta.getId());
        if (dataVolume != null) {
            final Path volumePath = Paths.get(dataVolume.getVolumePath());
            final Path rootFile = fileSystemStreamPathHelper.getRootPath(volumePath, meta, meta.getTypeName());
            if (Files.isRegularFile(rootFile)) {
                return Optional.of(rootFile);
            }
        }
        return Optional.empty();
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
