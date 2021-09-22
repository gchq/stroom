/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.data.store.impl.fs;

import stroom.data.store.impl.ScanVolumePathResult;
import stroom.data.store.impl.fs.shared.FsVolume;
import stroom.meta.api.MetaService;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaFields;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.security.api.SecurityContext;
import stroom.security.shared.PermissionNames;
import stroom.task.api.TaskContext;
import stroom.util.io.AbstractFileVisitor;
import stroom.util.io.FileUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;

import org.checkerframework.checker.units.qual.A;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import javax.inject.Inject;

/**
 * API used by the tasks to interface to the stream store under the bonnet.
 */
class FsOrphanFileFinder {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(FsOrphanFileFinder.class);

    private static final int BATCH_SIZE = 1000;

    private final FsPathHelper fileSystemStreamPathHelper;
    private final MetaService metaService;
    private final SecurityContext securityContext;

    @Inject
    public FsOrphanFileFinder(final FsPathHelper fileSystemStreamPathHelper,
                              final MetaService metaService,
                              final SecurityContext securityContext) {
        this.fileSystemStreamPathHelper = fileSystemStreamPathHelper;
        this.metaService = metaService;
        this.securityContext = securityContext;
    }

    public ScanVolumePathResult scanVolumePath(final FsVolume volume,
                                               final Consumer<Path> orphanConsumer,
                                               final long oldestDirTime,
                                               final TaskContext taskContext) {
        final FsOrphanFileFinderProgress cleanProgress = new FsOrphanFileFinderProgress(volume.getPath(), taskContext);
        return securityContext.secureResult(PermissionNames.DELETE_DATA_PERMISSION, () -> {
            final ScanVolumePathResult result = new ScanVolumePathResult();
            final Path directory = FileSystemUtil.createFileTypeRoot(volume);

            if (!Files.isDirectory(directory)) {
                LOGGER.debug(() -> "scanDirectory() - " +
                        FileUtil.getCanonicalPath(directory) +
                        " - Skipping as root is not a directory !!");
                return result;
            }

            final Map<Long, Set<Path>> fileMap = new HashMap<>();
            final AtomicLong dirAge = new AtomicLong();
            try {
                Files.walkFileTree(directory,
                        EnumSet.of(FileVisitOption.FOLLOW_LINKS),
                        Integer.MAX_VALUE,
                        new AbstractFileVisitor() {
                            @Override
                            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                                cleanProgress.addDir();
                                dirAge.set(attrs.creationTime().toMillis());
                                return super.preVisitDirectory(dir, attrs);
                            }

                            @Override
                            public FileVisitResult postVisitDirectory(final Path dir, final IOException exc) {
                                // If the dir is empty and old then record it.
                                final long age = dirAge.get();
                                if (age > 0 && age < oldestDirTime) {
                                    orphanConsumer.accept(dir);
                                }

                                return super.postVisitDirectory(dir, exc);
                            }

                            @Override
                            public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) {
                                dirAge.set(-1);
                                taskContext.info(() -> FileUtil.getCanonicalPath(file));
                                cleanProgress.addFile();

                                if (Thread.currentThread().isInterrupted()) {
                                    return FileVisitResult.TERMINATE;
                                }

                                // Process only raw zip repo files, i.e. files that have not already been created
                                // by the fragmenting process.
                                final long id = fileSystemStreamPathHelper.getId(file);
                                if (id == -1) {
                                    cleanProgress.addDeleteCount();
                                    orphanConsumer.accept(file);
                                } else {
                                    fileMap.computeIfAbsent(id, k -> new HashSet<>()).add(file);
                                }

                                if (fileMap.size() > BATCH_SIZE) {
                                    // Validate the batch of files against the DB.
                                    validateFiles(fileMap, cleanProgress, orphanConsumer);
                                    fileMap.clear();
                                }

                                return FileVisitResult.CONTINUE;
                            }
                        });
            } catch (final IOException | RuntimeException e) {
                LOGGER.error(e.getMessage(), e);
            }

            // Validate any remaining files against the DB.
            validateFiles(fileMap, cleanProgress, orphanConsumer);
            fileMap.clear();

            return result;
        });
    }

    private void validateFiles(final Map<Long, Set<Path>> fileMap,
                               final FsOrphanFileFinderProgress cleanProgress,
                               final Consumer<Path> deleteConsumer) {
        final ExpressionOperator.Builder builder = ExpressionOperator.builder().op(Op.OR);
        fileMap.keySet().forEach(id -> builder.addTerm(MetaFields.ID, Condition.EQUALS, id));
        final ExpressionOperator expression = builder.build();

        final FindMetaCriteria criteria = new FindMetaCriteria(expression);
        criteria.setPageRequest(new PageRequest(0, BATCH_SIZE));
        final ResultPage<Meta> resultPage = metaService.find(criteria);

        // If we have had the same number of results from the DB that we asked for then all is good.
        final List<Meta> metaList = resultPage.getValues();
        if (metaList.size() != fileMap.size()) {
            // Determine which files are orphans.
            for (final Meta meta : metaList) {
                fileMap.remove(meta.getId());
            }
            fileMap.values().forEach(list -> {
                list.forEach(file -> {
                    if (Files.isRegularFile(file)) {
                        cleanProgress.addDeleteCount();
                        deleteConsumer.accept(file);
                    }
                });
            });
        }
    }
}
