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

package stroom.streamstore.fs;

import event.logging.BaseAdvancedQueryItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.entity.CriteriaLoggingUtil;
import stroom.entity.StroomEntityManager;
import stroom.entity.SupportsCriteriaLogging;
import stroom.entity.shared.BaseResultList;
import stroom.entity.util.HqlBuilder;
import stroom.node.shared.Volume;
import stroom.security.Security;
import stroom.security.shared.PermissionNames;
import stroom.streamstore.FileArrayList;
import stroom.streamstore.FindStreamVolumeCriteria;
import stroom.streamstore.ScanVolumePathResult;
import stroom.streamstore.StreamMaintenanceService;
import stroom.streamstore.StreamRange;
import stroom.streamstore.StreamTypeService;
import stroom.streamstore.shared.Stream;
import stroom.streamstore.shared.StreamVolume;
import stroom.util.io.FileUtil;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * API used by the tasks to interface to the stream store under the bonnet.
 */
@Singleton
// @Transactional
public class FileSystemStreamMaintenanceService
        implements StreamMaintenanceService, SupportsCriteriaLogging<FindStreamVolumeCriteria> {
    /**
     * Here are our types of chart query. We use a prefix to understand the
     * value for the dynamic names (e.g. Fee - XYZ)
     */
//    public static final String STYLE_STREAM_PERIOD = "Stream Period Total";
//    public static final String PREFIX_STREAM_TYPE = "Type - ";
//    public static final String PREFIX_STAGE = "Stage - ";
//    public static final String PREFIX_GROUP = "Group - ";
//    public static final String PREFIX_NODE = "Node - ";
//    public static final String PREFIX_VOLUME = "Volume - ";
//    public static final String PREFIX_FEED = "Feed - ";
    private static final Logger LOGGER = LoggerFactory.getLogger(FileSystemStreamMaintenanceService.class);

    private final StroomEntityManager entityManager;
    private final StreamTypeService streamTypeService;
    private final Security security;

    @Inject
    public FileSystemStreamMaintenanceService(final StroomEntityManager entityManager,
                                              @Named("cachedStreamTypeService") final StreamTypeService streamTypeService,
                                              final Security security) {
        this.entityManager = entityManager;
        this.streamTypeService = streamTypeService;
        this.security = security;
    }

//    @Override
//    public Long deleteStreamVolume(final StreamVolume streamVolume) {
//        return security.secureResult(PermissionNames.DELETE_DATA_PERMISSION, () -> {
//            deleteStreamVolume(Arrays.asList(streamVolume));
//            return 1L;
//        });
//    }

    @SuppressWarnings("unchecked")
    @Override
    // @Transactional
    public BaseResultList<StreamVolume> find(final FindStreamVolumeCriteria criteria) {
        return security.secureResult(PermissionNames.DELETE_DATA_PERMISSION, () -> {
            if (!criteria.isValidCriteria()) {
                throw new IllegalArgumentException("Not enough criteria to run");
            }

            final HqlBuilder sql = new HqlBuilder();
            sql.append("SELECT sv FROM ");
            sql.append(StreamVolume.class.getName());
            sql.append(" sv");
            sql.append(" WHERE 1=1");

            sql.appendEntityIdSetQuery("sv.volume.node", criteria.getNodeIdSet());
            sql.appendEntityIdSetQuery("sv.volume", criteria.getVolumeIdSet());
            sql.appendEntityIdSetQuery("sv.stream", criteria.getStreamIdSet());
            sql.appendPrimitiveValueSetQuery("sv.stream.pstatus", criteria.getStreamStatusSet());

            if (criteria.getStreamRange() != null && criteria.getStreamRange().getStreamTypePath() != null) {
                sql.append(" AND sv.stream.streamType.path = ");
                sql.arg(criteria.getStreamRange().getStreamTypePath());
            }
            if (criteria.getStreamRange() != null && criteria.getStreamRange().isFileLocation()) {
                sql.appendRangeQuery("sv.stream.id", criteria.getStreamRange());
                sql.appendRangeQuery("sv.stream.createMs", criteria.getStreamRange().getCreatePeriod());
            }
            // Create the query
            final List<StreamVolume> results = entityManager.executeQueryResultList(sql, criteria);

            return BaseResultList.createCriterialBasedList(results, criteria);
        });
    }

//    @Override
////    @Secured(feature = Stream.DOCUMENT_TYPE, permission = DocumentPermissionNames.UPDATE)
//    public StreamVolume save(final StreamVolume streamVolume) {
//        return entityManager.saveEntity(streamVolume);
//    }

    //    @Override
//    @Secured(feature = Stream.DOCUMENT_TYPE, permission = DocumentPermissionNames.UPDATE)
    public Stream save(final Stream stream) {
        return entityManager.saveEntity(stream);
    }

//    //    @Secured(feature = Stream.DOCUMENT_TYPE, permission = DocumentPermissionNames.DELETE)
//    public Long deleteStreamVolume(final Collection<StreamVolume> toDelete) {
//        if (deleteFileSystemFiles(toDelete)) {
//            for (final StreamVolume metaDataVolume : toDelete) {
//                entityManager.deleteEntity(metaDataVolume);
//            }
//            return Long.valueOf(toDelete.size());
//        }
//        return 0L;
//    }
//
//    private boolean deleteFileSystemFiles(final Collection<StreamVolume> toDelete) {
//        boolean allOk = true;
//
//        for (final StreamVolume streamVolume : toDelete) {
//            final Path rootFile = FileSystemStreamTypeUtil.createRootStreamFile(streamVolume.getVolume(),
//                    streamVolume.getStream(), streamTypeService.load(streamVolume.getStream().getStreamType()));
//            allOk &= deleteAllFiles(rootFile);
//        }
//
//        return allOk;
//    }
//
//    private boolean deleteAllFiles(final Path file) {
//        boolean allOk = true;
//        if (Files.isRegularFile(file)) {
//            if (!FileUtil.delete(file)) {
//                LOGGER.error("Failed to delete file {}", FileUtil.getCanonicalPath(file));
//                allOk = false;
//            }
//            final List<Path> kids = FileSystemStreamTypeUtil.findAllDescendantStreamFileList(file);
//
//            for (final Path kid : kids) {
//                if (!FileUtil.delete(kid)) {
//                    LOGGER.error("Failed to delete file {}", FileUtil.getCanonicalPath(kid));
//                    allOk = false;
//                }
//            }
//        }
//        return allOk;
//    }

    @SuppressWarnings("unchecked")
//    @Override
        // @Transactional
    public FileArrayList findAllStreamFile(final Stream stream) {
        final FileArrayList results = new FileArrayList();
        final HqlBuilder sql = new HqlBuilder();
        sql.append("SELECT sv FROM ");
        sql.append(StreamVolume.class.getName());
        sql.append(" sv");
        sql.append(" WHERE sv.stream.id = ");
        sql.arg(stream.getId());
        final List<StreamVolume> volumeMatches = entityManager.executeQueryResultList(sql);

        for (final StreamVolume volumeMatch : volumeMatches) {
            final Path rootFile = FileSystemStreamTypeUtil.createRootStreamFile(volumeMatch.getVolume(),
                    volumeMatch.getStream(), streamTypeService.load(volumeMatch.getStream().getStreamType()));
            if (Files.isRegularFile(rootFile)) {
                results.add(rootFile);
                results.addAll(FileSystemStreamTypeUtil.findAllDescendantStreamFileList(rootFile));
            }
        }

        return results;
    }

    @Override
    // @Transactional
    public ScanVolumePathResult scanVolumePath(final Volume volume, final boolean doDelete, final String repoPath,
                                               final long oldFileAge) {
        return security.secureResult(PermissionNames.DELETE_DATA_PERMISSION, () -> {
            final ScanVolumePathResult result = new ScanVolumePathResult();

            final long oldFileTime = System.currentTimeMillis() - oldFileAge;

            final Map<String, List<String>> filesKeyedByBaseName = new HashMap<>();
            final Map<String, StreamVolume> streamsKeyedByBaseName = new HashMap<>();
            final Path directory;

            if (repoPath != null && !repoPath.isEmpty()) {
                directory = FileSystemUtil.createFileTypeRoot(volume).resolve(repoPath);
            } else {
                directory = FileSystemUtil.createFileTypeRoot(volume);
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
            checkEmptyDirectory(result, doDelete, directory, oldFileTime, kids);

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

    private void buildStreamsKeyedByBaseName(final Volume volume, final String repoPath,
                                             final Map<String, StreamVolume> streamsKeyedByBaseName) {
        // OK we have build up a list of files located in the directory
        // Now see what is there as per the database.
        final FindStreamVolumeCriteria criteria = new FindStreamVolumeCriteria();

        // Skip the first dir separator
        criteria.setStreamRange(new StreamRange(repoPath));
        criteria.obtainVolumeIdSet().add(volume);

        // Only process the dir if it is a location that can have files
        if (criteria.getStreamRange().isFileLocation() && !criteria.getStreamRange().isInvalidPath()) {
            final List<StreamVolume> matches = find(criteria);

            for (final StreamVolume streamVolume : matches) {
                streamsKeyedByBaseName.put(FileSystemStreamTypeUtil.getBaseName(streamVolume.getStream()),
                        streamVolume);
            }
        }
    }

    private void buildFilesKeyedByBaseName(final ScanVolumePathResult result, final String repoPath,
                                           final Map<String, List<String>> filesKeyedByBaseName, final Path directory, final List<String> kids) {
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

    private void tryDelete(final ScanVolumePathResult result, final boolean doDeleete, final Path deleteFile,
                           final long oldFileTime) {
        try {
            final long lastModified = Files.getLastModifiedTime(deleteFile).toMillis();

            if (lastModified < oldFileTime) {
                if (doDeleete) {
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

    private void checkEmptyDirectory(final ScanVolumePathResult result, final boolean doDeleete, final Path directory,
                                     final long oldFileTime, final List<String> kids) {
        if (kids == null || kids.size() == 0) {
            tryDelete(result, doDeleete, directory, oldFileTime);
        }
    }

    private void deleteUnknownFiles(final ScanVolumePathResult result, final boolean doDelete, final Path directory,
                                    final long oldFileTime, final Map<String, List<String>> filesKeyedByBaseName,
                                    final Map<String, StreamVolume> streamsKeyedByBaseName) {
        // OK now we can go through all the files that exist on the file
        // system and delete out as required
        for (final Entry<String, List<String>> entry : filesKeyedByBaseName.entrySet()) {
            final String fsBaseName = entry.getKey();
            final List<String> files = entry.getValue();

            final StreamVolume md = streamsKeyedByBaseName.get(fsBaseName);
            // Case 1 - No stream volume found !
            if (md == null) {
                for (final String file : files) {
                    tryDelete(result, doDelete, directory.resolve(file), oldFileTime);
                }
            } else {
                // Case 2 - match
                for (final String file : files) {
                    LOGGER.debug("processDirectory() - {}/{} belongs to stream {}",
                            directory,
                            file,
                            md.getStream().getId()
                    );
                }
            }
        }

        // Update any streams that don't have a matching file
        streamsKeyedByBaseName.keySet().stream()
                .filter(streamBaseName -> !filesKeyedByBaseName.containsKey(streamBaseName))
                .forEach(streamBaseName -> LOGGER.error("processDirectory() - Missing Files for {}/{}", directory,
                        streamBaseName));
    }

//    /**
//     * Root match
//     */
//    public boolean isChartTypeStreamPeriod(final String chartName) {
//        return chartName.startsWith(STYLE_STREAM_PERIOD);
//    }

    @Override
    public FindStreamVolumeCriteria createCriteria() {
        return new FindStreamVolumeCriteria();
    }

    @Override
    public void appendCriteria(final List<BaseAdvancedQueryItem> items, final FindStreamVolumeCriteria criteria) {
        CriteriaLoggingUtil.appendRangeTerm(items, "streamRange", criteria.getStreamRange());
        CriteriaLoggingUtil.appendCriteriaSet(items, "streamStatusSet", criteria.getStreamStatusSet());
        CriteriaLoggingUtil.appendEntityIdSet(items, "nodeIdSet", criteria.getNodeIdSet());
        CriteriaLoggingUtil.appendEntityIdSet(items, "volumeIdSet", criteria.getVolumeIdSet());
        CriteriaLoggingUtil.appendEntityIdSet(items, "streamIdSet", criteria.getStreamIdSet());
        CriteriaLoggingUtil.appendPageRequest(items, criteria.getPageRequest());
    }
}
