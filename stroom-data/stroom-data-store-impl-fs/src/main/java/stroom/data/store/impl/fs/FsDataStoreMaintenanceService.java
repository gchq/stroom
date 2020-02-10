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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.data.store.impl.DataStoreMaintenanceService;
import stroom.data.store.impl.ScanVolumePathResult;
import stroom.data.store.impl.fs.DataVolumeDao.DataVolume;
import stroom.data.store.impl.fs.shared.FsVolume;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaFields;
import stroom.meta.api.MetaService;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.security.api.SecurityContext;
import stroom.security.shared.PermissionNames;
import stroom.util.io.FileUtil;
import stroom.util.shared.ResultList;
import stroom.util.shared.CriteriaSet;
import stroom.util.shared.PageRequest;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;

/**
 * API used by the tasks to interface to the stream store under the bonnet.
 */
@Singleton
class FsDataStoreMaintenanceService implements DataStoreMaintenanceService {
    private static final Logger LOGGER = LoggerFactory.getLogger(FsDataStoreMaintenanceService.class);

    private final FsPathHelper fileSystemStreamPathHelper;
    private final DataVolumeService dataVolumeService;
    private final MetaService metaService;
    private final SecurityContext securityContext;

    private final FsFeedPathDao fileSystemFeedPaths;
    private final FsTypePathDao fileSystemTypePaths;

    @Inject
    public FsDataStoreMaintenanceService(final FsPathHelper fileSystemStreamPathHelper,
                                         final FsFeedPathDao fileSystemFeedPaths,
                                         final FsTypePathDao fileSystemTypePaths,
                                         final DataVolumeService dataVolumeService,
                                         final MetaService metaService,
                                         final SecurityContext securityContext) {
        this.fileSystemStreamPathHelper = fileSystemStreamPathHelper;
        this.fileSystemFeedPaths = fileSystemFeedPaths;
        this.fileSystemTypePaths = fileSystemTypePaths;
        this.dataVolumeService = dataVolumeService;
        this.metaService = metaService;
        this.securityContext = securityContext;
    }

    List<Path> findAllStreamFile(final Meta meta) {
        final DataVolume dataVolume = dataVolumeService.findDataVolume(meta.getId());
        if (dataVolume != null) {
            final Path rootFile = fileSystemStreamPathHelper.getRootPath(dataVolume.getVolumePath(), meta, meta.getTypeName());
            final List<Path> results = new ArrayList<>();
            if (Files.isRegularFile(rootFile)) {
                results.add(rootFile);
                results.addAll(fileSystemStreamPathHelper.findAllDescendantStreamFileList(rootFile));
            }
            return results;
        }

        return Collections.emptyList();
    }

    @Override
    public ScanVolumePathResult scanVolumePath(final FsVolume volume,
                                               final boolean doDelete,
                                               final String repoPath,
                                               final Duration oldFileAge) {
        Objects.requireNonNull(oldFileAge);
        return securityContext.secureResult(PermissionNames.DELETE_DATA_PERMISSION, () -> {
            final ScanVolumePathResult result = new ScanVolumePathResult();

            final long oldFileTime = System.currentTimeMillis() - oldFileAge.toMillis();

            final Map<String, List<String>> filesKeyedByBaseName = new HashMap<>();
            final Map<String, DataVolume> streamsKeyedByBaseName = new HashMap<>();
            final Path directory;
            final Path volumeRoot = FileSystemUtil.createFileTypeRoot(volume);

            if (repoPath != null && !repoPath.isEmpty()) {
                directory = volumeRoot.resolve(repoPath);
            } else {
                directory = volumeRoot;
            }

            if (!Files.isDirectory(directory)) {
                LOGGER.debug("scanDirectory() - {} - Skipping as root is not a directory !!", directory);
                return result;
            }
            // Get the list of kids
            final List<String> kids = new ArrayList<>();
            try (final DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
                stream.forEach(file -> kids.add(file.getFileName().toString()));
            } catch (final IOException e) {
                LOGGER.error(e.getMessage(), e);
            }

            LOGGER.debug("scanDirectory() - {}", FileUtil.getCanonicalPath(directory));

            result.setFileCount(kids.size());

            // Here we check the file system for files before querying the database.
            // The idea is that entries are written in the database before the file
            // system. We can safely delete entries on the file system that do not
            // have a entries on the database.
            try {
                if (!Files.isSameFile(volumeRoot, directory)) {
                    checkEmptyDirectory(result, doDelete, directory, oldFileTime, kids);
                }
            } catch (final IOException e) {
                LOGGER.error(e.getMessage(), e);
            }

            // Loop around all the kids build a list of file names or sub processing
            // directories.
            buildFilesKeyedByBaseName(result, repoPath, filesKeyedByBaseName, directory, kids);

            if (repoPath != null && !repoPath.isEmpty()) {
                buildStreamsKeyedByBaseName(volume, repoPath, streamsKeyedByBaseName);

                deleteUnknownFiles(result, doDelete, directory, oldFileTime, filesKeyedByBaseName, streamsKeyedByBaseName);

            }

            return result;
        });
    }

    private void buildStreamsKeyedByBaseName(final FsVolume volume,
                                             final String repoPath,
                                             final Map<String, DataVolume> streamsKeyedByBaseName) {
        try {
            // We need to find streams that match the repo path.
            final ResultList<Meta> matchingStreams = findMatchingStreams(repoPath);

            // If we haven't found any streams then give up.
            if (matchingStreams.size() > 0) {
                // OK we have built up a list of files located in the directory
                // Now see what is there as per the database.
                final FindDataVolumeCriteria criteria = new FindDataVolumeCriteria();

                final Map<Long, Meta> streamMap = new HashMap<>();
                final CriteriaSet<Long> streamIdSet = criteria.obtainMetaIdSet();
                matchingStreams.forEach(stream -> {
                    final long id = stream.getId();
                    streamMap.put(id, stream);
                    streamIdSet.add(id);
                });
                criteria.obtainVolumeIdSet().add(volume.getId());

                final List<DataVolume> matches = dataVolumeService.find(criteria);

                for (final DataVolume streamVolume : matches) {
                    final Meta meta = streamMap.get(streamVolume.getStreamId());
                    if (meta != null) {
                        streamsKeyedByBaseName.put(fileSystemStreamPathHelper.getBaseName(meta), streamVolume);
                    }
                }
            }
        } catch (final RuntimeException e) {
            LOGGER.warn(e.getMessage(), e);
        }
    }

    /**
     * Find streams in the stream meta service that are relevant to the supplied repository path.
     *
     * @param repoPath The repository path to find relevant streams for.
     * @return A list of streams that are relevant to the supplied repository path.
     */
    private ResultList<Meta> findMatchingStreams(final String repoPath) {
        try {
            // We need to find streams that match the repo path.
            final Optional<ExpressionOperator> optional = pathToStreamExpression(repoPath);
            return optional.map(expression -> {
                final FindMetaCriteria criteria = new FindMetaCriteria(expression);
                criteria.setPageRequest(new PageRequest(0L, 1000));
                return metaService.find(criteria);
            }).orElseGet(() -> ResultList.createUnboundedList(Collections.emptyList()));
        } catch (final RuntimeException e) {
            LOGGER.debug(e.getMessage(), e);
            LOGGER.warn(e.getMessage());
        }

        return ResultList.createUnboundedList(Collections.emptyList());
    }

    /**
     * Turn a repository path into an expression to find matching streams.
     *
     * @param repoPath The repository path to create the expression from.
     * @return An expression that can be used to query the stream meta service.
     */
    private Optional<ExpressionOperator> pathToStreamExpression(final String repoPath) {
        final String[] parts = repoPath.split("/");

        if (parts.length < 4) {
            // Not a stream path
            return Optional.empty();
        }

        final ExpressionOperator.Builder builder = new ExpressionOperator.Builder(Op.AND);
        try {
            final String streamTypeName = fileSystemTypePaths.getType(parts[0]);
            builder.addTerm(MetaFields.TYPE_NAME, Condition.EQUALS, streamTypeName);

            final String fromDateString = parts[1] + "-" + parts[2] + "-" + parts[3];
            final LocalDate localDate = LocalDate.parse(fromDateString, DateTimeFormatter.ISO_LOCAL_DATE);
            final String toDateString = localDate.plusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE);

            builder.addTerm(MetaFields.CREATE_TIME, Condition.GREATER_THAN_OR_EQUAL_TO, fromDateString + "T00:00:00.000Z");
            builder.addTerm(MetaFields.CREATE_TIME, Condition.LESS_THAN, toDateString + "T00:00:00.000Z");

            final StringBuilder numberPart = new StringBuilder();
            for (int i = 4; i < parts.length; i++) {
                numberPart.append(parts[i]);
            }

            long fromId = 1L;
            if (numberPart.length() > 0) {
                try {
                    final long dirNumber = Long.parseLong(numberPart.toString());

                    // E.g. 001/110 would contain numbers 1,110,000 to 1,110,999
                    // 001/111 would contain numbers 1,111,000 to 1,111,999
                    fromId = dirNumber * 1000L;

                } catch (final RuntimeException e) {
                    // Not a stream path
                    throw new RuntimeException("Not a valid repository path '" + repoPath + "'");
                }
            }

            long toId = fromId + 1000L;

            builder.addTerm(MetaFields.ID, Condition.GREATER_THAN_OR_EQUAL_TO, fromId);
            builder.addTerm(MetaFields.ID, Condition.LESS_THAN, toId);

            return Optional.of(builder.build());
        } catch (final RuntimeException e) {
            // Not a stream path
            throw new RuntimeException("Not a valid repository path");
        }
    }

    private void buildFilesKeyedByBaseName(final ScanVolumePathResult result,
                                           final String repoPath,
                                           final Map<String, List<String>> filesKeyedByBaseName,
                                           final Path directory,
                                           final List<String> kids) {
        if (kids != null) {
            for (final String kid : kids) {
                final Path kidFile = directory.resolve(kid);

                if (Files.isDirectory(kidFile)) {
                    if (repoPath != null && !repoPath.isEmpty()) {
                        result.addChildDirectory(repoPath + FileSystemUtil.SEPERATOR_CHAR + kid);
                    } else {
                        result.addChildDirectory(kid);
                    }

                } else {
                    // Add to our list
                    String baseName = kid;
                    final int baseNameSplit = kid.indexOf(".");
                    if (baseNameSplit != -1) {
                        baseName = kid.substring(0, baseNameSplit);
                    }
                    // Ignore any OS hidden files ".xyz" (e.g.
                    // .nfs0000000001a5674600004012)
                    if (baseName.length() > 0) {
                        filesKeyedByBaseName.computeIfAbsent(baseName, k -> new ArrayList<>()).add(kid);
                    }
                }
            }
        }
    }

    private void tryDelete(final ScanVolumePathResult result,
                           final boolean doDelete,
                           final Path deleteFile,
                           final long oldFileTime) {
        try {
            final long lastModified = Files.getLastModifiedTime(deleteFile).toMillis();

            if (lastModified < oldFileTime) {
                if (doDelete) {
                    try {
                        Files.delete(deleteFile);
                        LOGGER.debug("tryDelete() - Deleted file {}", FileUtil.getCanonicalPath(deleteFile));
                    } catch (final IOException e) {
                        LOGGER.error("tryDelete() - Failed to delete file {}", FileUtil.getCanonicalPath(deleteFile));
                    }
                }
                result.addDelete(FileUtil.getCanonicalPath(deleteFile));

            } else {
                LOGGER.debug("tryDelete() - File too new to delete {}", FileUtil.getCanonicalPath(deleteFile));
                result.incrementTooNewToDeleteCount();
            }
        } catch (final IOException e) {
            LOGGER.error("tryDelete() - Failed to delete file {}", FileUtil.getCanonicalPath(deleteFile), e);
        }
    }

    private void checkEmptyDirectory(final ScanVolumePathResult result, final boolean doDelete, final Path directory,
                                     final long oldFileTime, final List<String> kids) {
        if (kids == null || kids.size() == 0) {
            tryDelete(result, doDelete, directory, oldFileTime);
        }
    }

    private void deleteUnknownFiles(final ScanVolumePathResult result,
                                    final boolean doDelete,
                                    final Path directory,
                                    final long oldFileTime,
                                    final Map<String, List<String>> filesKeyedByBaseName,
                                    final Map<String, DataVolume> streamsKeyedByBaseName) {
        // OK now we can go through all the files that exist on the file
        // system and delete out as required
        for (final Entry<String, List<String>> entry : filesKeyedByBaseName.entrySet()) {
            final String fsBaseName = entry.getKey();
            final List<String> files = entry.getValue();

            final DataVolume md = streamsKeyedByBaseName.get(fsBaseName);
            // Case 1 - No stream volume found !
            if (md == null) {
                for (final String file : files) {
                    tryDelete(result, doDelete, directory.resolve(file), oldFileTime);
                }
            } else {
                // Case 2 - match
                if (LOGGER.isDebugEnabled()) {
                    for (final String file : files) {
                        LOGGER.debug("processDirectory() - {}/{} belongs to stream {}",
                                directory,
                                file,
                                md.getStreamId()
                        );
                    }
                }
            }
        }

        // Update any streams that don't have a matching file
        streamsKeyedByBaseName.keySet().stream()
                .filter(streamBaseName -> !filesKeyedByBaseName.containsKey(streamBaseName))
                .forEach(streamBaseName -> LOGGER.error("processDirectory() - Missing Files for {}/{}", directory,
                        streamBaseName));
    }
}
