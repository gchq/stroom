/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.data.store.impl.fs;

import stroom.data.store.impl.ScanVolumePathResult;
import stroom.data.store.impl.fs.shared.FsVolume;
import stroom.meta.api.MetaService;
import stroom.security.api.SecurityContext;
import stroom.security.shared.AppPermission;
import stroom.task.api.TaskContext;
import stroom.util.io.AbstractFileVisitor;
import stroom.util.io.FileUtil;
import stroom.util.io.PathCreator;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import jakarta.inject.Inject;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * API used by the tasks to interface to the stream store under the bonnet.
 */
class FsOrphanFileFinder {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(FsOrphanFileFinder.class);

    private static final int BATCH_SIZE = 1_000;

    private final FsPathHelper fileSystemStreamPathHelper;
    private final MetaService metaService;
    private final SecurityContext securityContext;
    private final PathCreator pathCreator;

    @Inject
    public FsOrphanFileFinder(final FsPathHelper fileSystemStreamPathHelper,
                              final MetaService metaService,
                              final SecurityContext securityContext,
                              final PathCreator pathCreator) {
        this.fileSystemStreamPathHelper = fileSystemStreamPathHelper;
        this.metaService = metaService;
        this.securityContext = securityContext;
        this.pathCreator = pathCreator;
    }

    public ScanVolumePathResult scanVolumePath(final FsVolume volume,
                                               final Consumer<Path> orphanConsumer,
                                               final Instant oldestDirTime,
                                               final TaskContext taskContext) {
        final String volumePathStr = pathCreator.toAppPath(volume.getPath()).toString();

        final FsOrphanFileFinderProgress cleanProgress = new FsOrphanFileFinderProgress(
                volumePathStr,
                taskContext);
        return securityContext.secureResult(AppPermission.DELETE_DATA_PERMISSION, () -> {
            final ScanVolumePathResult result = new ScanVolumePathResult();
            final Path directory = FileSystemUtil.createFileTypeRoot(volumePathStr);

            if (!Files.isDirectory(directory)) {
                LOGGER.debug(() -> "scanDirectory() - " +
                        FileUtil.getCanonicalPath(directory) +
                        " - Skipping as root is not a directory !!");
                return result;
            }
            LOGGER.debug(() -> LogUtil.message("{} - Scanning directory {} with oldestDirTime {}",
                    FsOrphanFileFinderExecutor.TASK_NAME, directory, oldestDirTime));

            final Map<Long, Set<Path>> fileMap = new HashMap<>();
            final Map<Path, Instant> dirAges = new HashMap<>();
            try {
                Files.walkFileTree(directory,
                        EnumSet.of(FileVisitOption.FOLLOW_LINKS),
                        Integer.MAX_VALUE,
                        new AbstractFileVisitor() {
                            @Override
                            public FileVisitResult preVisitDirectory(final Path dir, final BasicFileAttributes attrs) {
                                cleanProgress.addDir();
                                // Remember the dir age.
                                final Instant instant = Instant.ofEpochMilli(attrs.lastModifiedTime().toMillis());
                                dirAges.put(dir, instant);

                                // The parent dir has child dirs so we won't be considering the dir for deletion.
                                final Path parent = dir.getParent();
                                if (parent != null) {
                                    dirAges.remove(parent);
                                }

                                return super.preVisitDirectory(dir, attrs);
                            }

                            @Override
                            public FileVisitResult postVisitDirectory(final Path dir, final IOException exc) {
                                // If the dir is empty and old then record it.
                                final Instant age = dirAges.remove(dir);
                                if (age != null && age.isBefore(oldestDirTime)) {
                                    LOGGER.trace(() -> "Orphan dir: " + FileUtil.getCanonicalPath(dir));
                                    orphanConsumer.accept(dir);
                                } else {
                                    LOGGER.trace("Ignoring recently created dir {}", dir);
                                }

                                return super.postVisitDirectory(dir, exc);
                            }

                            @Override
                            public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) {
                                // The parent dir has files so we won't be considering the dir for deletion.
                                dirAges.remove(file.getParent());

                                taskContext.info(() -> FileUtil.getCanonicalPath(file));
                                cleanProgress.addFile();

                                if (Thread.currentThread().isInterrupted() || taskContext.isTerminated()) {
                                    return FileVisitResult.TERMINATE;
                                }

                                // Process only raw zip repo files, i.e. files that have not already been created
                                // by the fragmenting process.
                                final long id = fileSystemStreamPathHelper.getId(file);
                                LOGGER.trace(() -> "Got id = " + id + " for file " + FileUtil.getCanonicalPath(file));

                                if (id == -1) {
                                    LOGGER.trace(() -> "Orphan file as no id: " + FileUtil.getCanonicalPath(file));
                                    cleanProgress.addOrphanCount();
                                    orphanConsumer.accept(file);
                                } else {
                                    fileMap.computeIfAbsent(id, k -> new HashSet<>()).add(file);
                                }

                                if (fileMap.size() >= BATCH_SIZE) {
                                    // Validate the batch of files against the DB.
                                    validateFiles(fileMap, cleanProgress, orphanConsumer, taskContext);
                                    fileMap.clear();
                                }

                                return FileVisitResult.CONTINUE;
                            }
                        });
            } catch (final IOException | RuntimeException e) {
                LOGGER.error(e.getMessage(), e);
            }

            if (!Thread.currentThread().isInterrupted() && !taskContext.isTerminated()) {
                // Validate any remaining files against the DB.
                validateFiles(fileMap, cleanProgress, orphanConsumer, taskContext);
            }
            fileMap.clear();

            return result;
        });
    }

    private void validateFiles(final Map<Long, Set<Path>> fileMap,
                               final FsOrphanFileFinderProgress cleanProgress,
                               final Consumer<Path> orphanConsumer,
                               final TaskContext taskContext) {
        // See if all the meta ids from the files exist in the db
        final Set<Long> metaIdSet = metaService.exists(fileMap.keySet());

//        // If we have had the same number of results from the DB that we asked for then all is good.
//        final Se<Meta> metaList = resultPage.getValues();
        if (metaIdSet.size() != fileMap.size()) {
            LOGGER.debug(() -> LogUtil.message(
                    "metaIdSet is is different size to file map: " +
                            "metaIdSet.size(): {}, fileMap.size(): {}, batch size {}",
                    metaIdSet.size(), fileMap.size(), BATCH_SIZE));

            // Determine which files are orphans.
            metaIdSet.forEach(fileMap::remove);

            fileMap.values()
                    .forEach(list -> list
                            .stream()
                            .takeWhile(item ->
                                    !Thread.currentThread().isInterrupted() && !taskContext.isTerminated())
                            .forEach(file -> {
                                LOGGER.trace(() -> "Orphan file: " + FileUtil.getCanonicalPath(file));
                                if (Files.isRegularFile(file)) {
                                    cleanProgress.addOrphanCount();
                                    orphanConsumer.accept(file);
                                }
                            }));
        }
    }
}
