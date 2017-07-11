/*
 * Copyright 2016 Crown Copyright
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

package stroom.streamstore.server.fs;

import event.logging.BaseAdvancedQueryItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import stroom.entity.server.CriteriaLoggingUtil;
import stroom.entity.server.SupportsCriteriaLogging;
import stroom.entity.server.util.HqlBuilder;
import stroom.entity.server.util.StroomEntityManager;
import stroom.entity.shared.BaseResultList;
import stroom.feed.shared.FeedService;
import stroom.node.shared.Volume;
import stroom.security.Secured;
import stroom.streamstore.server.FileArrayList;
import stroom.streamstore.server.FindStreamVolumeCriteria;
import stroom.streamstore.server.ScanVolumePathResult;
import stroom.streamstore.server.StreamMaintenanceService;
import stroom.streamstore.server.StreamRange;
import stroom.streamstore.shared.Stream;
import stroom.streamstore.shared.StreamTypeService;
import stroom.streamstore.shared.StreamVolume;

import javax.annotation.Resource;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * API used by the tasks to interface to the stream store under the bonnet.
 */
@Transactional
@Secured(Stream.DELETE_DATA_PERMISSION)
@Component
public class FileSystemStreamMaintenanceService
        implements StreamMaintenanceService, SupportsCriteriaLogging<FindStreamVolumeCriteria> {
    /**
     * Here are our types of chart query. We use a prefix to understand the
     * value for the dynamic names (e.g. Fee - XYZ)
     */
    public static final String STYLE_STREAM_PERIOD = "Stream Period Total";
    public static final String PREFIX_STREAM_TYPE = "Type - ";
    public static final String PREFIX_STAGE = "Stage - ";
    public static final String PREFIX_GROUP = "Group - ";
    public static final String PREFIX_NODE = "Node - ";
    public static final String PREFIX_VOLUME = "Volume - ";
    public static final String PREFIX_FEED = "Feed - ";
    private static final Logger LOGGER = LoggerFactory.getLogger(FileSystemStreamMaintenanceService.class);

    @Resource
    private StroomEntityManager entityManager;
    @Resource
    private FeedService feedService;
    @Resource(name = "cachedStreamTypeService")
    private StreamTypeService streamTypeService;

    @Override
    public Long deleteStreamVolume(final StreamVolume streamVolume) {
        deleteStreamVolume(Arrays.asList(streamVolume));
        return 1L;
    }

    @SuppressWarnings("unchecked")
    @Override
    @Transactional(readOnly = true)
    public BaseResultList<StreamVolume> find(final FindStreamVolumeCriteria criteria) {
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
    }

    @Override
//    @Secured(feature = Stream.ENTITY_TYPE, permission = DocumentPermissionNames.UPDATE)
    public StreamVolume save(final StreamVolume streamVolume) {
        return entityManager.saveEntity(streamVolume);
    }

    @Override
//    @Secured(feature = Stream.ENTITY_TYPE, permission = DocumentPermissionNames.UPDATE)
    public Stream save(final Stream stream) {
        return entityManager.saveEntity(stream);
    }

    //    @Secured(feature = Stream.ENTITY_TYPE, permission = DocumentPermissionNames.DELETE)
    public Long deleteStreamVolume(final Collection<StreamVolume> toDelete) {
        if (deleteFileSystemFiles(toDelete)) {
            for (final StreamVolume metaDataVolume : toDelete) {
                entityManager.deleteEntity(metaDataVolume);
            }
            return Long.valueOf(toDelete.size());
        }
        return 0L;
    }

    private boolean deleteFileSystemFiles(final Collection<StreamVolume> toDelete) {
        boolean allOk = true;

        for (final StreamVolume streamVolume : toDelete) {
            final File rootFile = FileSystemStreamTypeUtil.createRootStreamFile(streamVolume.getVolume(),
                    streamVolume.getStream(), streamTypeService.load(streamVolume.getStream().getStreamType()));
            allOk &= deleteAllFiles(rootFile);
        }

        return allOk;
    }

    private boolean deleteAllFiles(final File file) {
        boolean allOk = true;
        if (file.isFile()) {
            if (!file.delete()) {
                LOGGER.error("Failed to delete file {}", file.getAbsolutePath());
                allOk = false;
            }
            final List<File> kids = FileSystemStreamTypeUtil.findAllDescendantStreamFileList(file);

            for (final File kid : kids) {
                if (!kid.delete()) {
                    LOGGER.error("Failed to delete file {}", kid.getAbsolutePath());
                    allOk = false;
                }
            }
        }
        return allOk;
    }

    @SuppressWarnings("unchecked")
    @Override
    @Transactional(readOnly = true)
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
            final File rootFile = FileSystemStreamTypeUtil.createRootStreamFile(volumeMatch.getVolume(),
                    volumeMatch.getStream(), streamTypeService.load(volumeMatch.getStream().getStreamType()));
            if (rootFile.isFile()) {
                results.add(rootFile);
                results.addAll(FileSystemStreamTypeUtil.findAllDescendantStreamFileList(rootFile));
            }
        }

        return results;
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ScanVolumePathResult scanVolumePath(final Volume volume, final boolean doDelete, final String repoPath,
                                               final long oldFileAge) {
        final ScanVolumePathResult result = new ScanVolumePathResult();

        final long oldFileTime = System.currentTimeMillis() - oldFileAge;

        final Map<String, List<String>> filesKeyedByBaseName = new HashMap<>();
        final Map<String, StreamVolume> streamsKeyedByBaseName = new HashMap<>();
        final File directory;

        if (StringUtils.hasText(repoPath)) {
            directory = new File(FileSystemUtil.createFileTypeRoot(volume), repoPath);
        } else {
            directory = FileSystemUtil.createFileTypeRoot(volume);
        }

        if (!directory.isDirectory()) {
            LOGGER.debug("scanDirectory() - {} - Skipping as root is not a directory !!", directory);
            return result;
        }
        // Get the list of kids
        final String[] kids = directory.list();

        LOGGER.debug("scanDirectory() - {}", directory.getAbsolutePath());

        if (kids != null) {
            result.setFileCount(kids.length);
        }

        // Here we check the file system for files before querying the database.
        // The idea is that entries are written in the database before the file
        // system. We can safely delete entries on the file system that do not
        // have a entries on the database.
        checkEmptyDirectory(result, doDelete, directory, oldFileTime, kids);

        // Loop around all the kids build a list of file names or sub processing
        // directories.
        buildFilesKeyedByBaseName(result, repoPath, filesKeyedByBaseName, directory, kids);

        if (StringUtils.hasText(repoPath)) {
            buildStreamsKeyedByBaseName(volume, repoPath, streamsKeyedByBaseName);

            deleteUnknownFiles(result, doDelete, directory, oldFileTime, filesKeyedByBaseName, streamsKeyedByBaseName);

        }

        return result;
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
                                           final Map<String, List<String>> filesKeyedByBaseName, final File directory, final String[] kids) {
        if (kids != null) {
            for (int i = 0; i < kids.length; i++) {
                final File kidFile = new File(directory, kids[i]);

                if (kidFile.isDirectory()) {
                    if (StringUtils.hasText(repoPath)) {
                        result.addChildDirectory(repoPath + FileSystemUtil.SEPERATOR_CHAR + kids[i]);
                    } else {
                        result.addChildDirectory(kids[i]);
                    }

                } else {
                    // Add to our list
                    final String fileName = kids[i];
                    String baseName = fileName;
                    final int baseNameSplit = fileName.indexOf(".");
                    if (baseNameSplit != -1) {
                        baseName = fileName.substring(0, baseNameSplit);
                    }
                    // Ignore any OS hidden files ".xyz" (e.g.
                    // .nfs0000000001a5674600004012)
                    if (baseName.length() > 0) {
                        List<String> names = filesKeyedByBaseName.get(baseName);
                        if (names == null) {
                            names = new ArrayList<>();
                            filesKeyedByBaseName.put(baseName, names);
                        }
                        names.add(fileName);
                    }
                }
            }
        }
    }

    private void tryDelete(final ScanVolumePathResult result, final boolean doDeleete, final File deleteFile,
                           final long oldFileTime) {
        try {
            final long lastModified = deleteFile.lastModified();

            if (lastModified < oldFileTime) {
                if (doDeleete) {
                    if (deleteFile.delete()) {
                        LOGGER.debug("tryDelete() - Deleted file {}", deleteFile.getAbsolutePath());
                    } else {
                        LOGGER.error("tryDelete() - Failed to delete file {}", deleteFile.getAbsolutePath());
                    }
                }
                result.addDelete(deleteFile.getAbsolutePath());

            } else {
                LOGGER.debug("tryDelete() - File too new to delete {}", deleteFile.getAbsolutePath());
                result.incrementTooNewToDeleteCount();
            }
        } catch (final Exception ex) {
            LOGGER.error("tryDelete() - Failed to delete file {}", deleteFile.getAbsolutePath(), ex);
        }
    }

    private void checkEmptyDirectory(final ScanVolumePathResult result, final boolean doDeleete, final File directory,
                                     final long oldFileTime, final String[] kids) {
        if (kids == null || kids.length == 0) {
            tryDelete(result, doDeleete, directory, oldFileTime);
        }
    }

    private void deleteUnknownFiles(final ScanVolumePathResult result, final boolean doDelete, final File directory,
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
                    tryDelete(result, doDelete, new File(directory, file), oldFileTime);
                }
            } else {
                // Case 2 - match
                for (final String file : files) {
                    LOGGER.debug("processDirectory() - {}/{} belongs to stream {}", new Object[]{
                            directory,
                            file,
                            md.getStream().getId()
                    });
                }
            }
        }

        // Update any streams that don't have a matching file
        streamsKeyedByBaseName.keySet().stream()
                .filter(streamBaseName -> !filesKeyedByBaseName.containsKey(streamBaseName))
                .forEach(streamBaseName -> LOGGER.error("processDirectory() - Missing Files for {}/{}", directory,
                        streamBaseName));
    }

    /**
     * Root match
     */
    public boolean isChartTypeStreamPeriod(final String chartName) {
        return chartName.startsWith(STYLE_STREAM_PERIOD);
    }

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
