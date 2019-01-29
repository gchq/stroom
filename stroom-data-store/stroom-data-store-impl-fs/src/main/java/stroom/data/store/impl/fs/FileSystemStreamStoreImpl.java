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


import event.logging.util.DateUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.data.store.api.DataException;
import stroom.data.store.api.Source;
import stroom.data.store.api.Store;
import stroom.data.store.api.Target;
import stroom.data.store.impl.fs.DataVolumeService.DataVolume;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaFieldNames;
import stroom.meta.shared.MetaProperties;
import stroom.meta.shared.MetaService;
import stroom.meta.shared.Status;
import stroom.node.api.NodeInfo;
import stroom.node.shared.Node;
import stroom.node.shared.VolumeEntity;
import stroom.volume.VolumeService;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * A file system stream store.
 * </p>
 * <p>
 * <p>
 * Stores streams in the stream store indexed by some meta data.
 * </p>
 */
@Singleton
class FileSystemStreamStoreImpl implements Store {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileSystemStreamStoreImpl.class);

    private final FileSystemStreamPathHelper fileSystemStreamPathHelper;
    private final MetaService metaService;
    private final NodeInfo nodeInfo;
    private final VolumeService volumeService;
    private final DataVolumeService streamVolumeService;

    @Inject
    FileSystemStreamStoreImpl(final FileSystemStreamPathHelper fileSystemStreamPathHelper,
                              final MetaService metaService,
                              final NodeInfo nodeInfo,
                              final VolumeService volumeService,
                              final DataVolumeService streamVolumeService) {
        this.fileSystemStreamPathHelper = fileSystemStreamPathHelper;
        this.metaService = metaService;
        this.nodeInfo = nodeInfo;
        this.volumeService = volumeService;
        this.streamVolumeService = streamVolumeService;
    }

    @Override
    public Target openStreamTarget(final MetaProperties metaProperties) {
        LOGGER.debug("openStreamTarget() " + metaProperties);

        final Set<VolumeEntity> volumeSet = volumeService.getStreamVolumeSet(nodeInfo.getThisNode());
        if (volumeSet.isEmpty()) {
            throw new DataException("Failed to get lock as no writeable volumes");
        }

        // First time call (no file yet exists)
        final Meta meta = metaService.create(metaProperties);

        final Set<DataVolume> streamVolumes = streamVolumeService.createStreamVolumes(meta.getId(), volumeSet);
        final Set<String> rootPaths = streamVolumes.stream().map(DataVolume::getVolumePath).collect(Collectors.toSet());
        final String streamType = meta.getTypeName();
        final FileSystemStreamTarget target = FileSystemStreamTarget.create(metaService, fileSystemStreamPathHelper, meta, rootPaths, streamType, false);

        // Force Creation of the files
        target.getOutputStream();

        syncAttributes(meta, target);

        return target;
    }

    @Override
    public Target openExistingStreamTarget(final Meta meta) throws DataException {
        Objects.requireNonNull(meta, "Null meta");
        LOGGER.debug("openExistingStreamTarget() " + meta);

        // Lock the object
        final Set<DataVolume> streamVolumes = streamVolumeService.findStreamVolume(meta.getId());
        if (streamVolumes.isEmpty()) {
            throw new DataException("Not all volumes are unlocked");
        }
        final Meta lockedMeta = metaService.updateStatus(meta, Status.LOCKED, Status.UNLOCKED);
        final Set<String> rootPaths = streamVolumes.stream().map(DataVolume::getVolumePath).collect(Collectors.toSet());

        final String streamType = lockedMeta.getTypeName();
        final FileSystemStreamTarget target = FileSystemStreamTarget.create(metaService, fileSystemStreamPathHelper, lockedMeta, rootPaths,
                streamType, true);

        syncAttributes(lockedMeta, target);

        return target;
    }

//    @Override
//    public void closeStreamTarget(final Target streamTarget) {
//        final FileSystemStreamTarget fileSystemStreamTarget = (FileSystemStreamTarget) streamTarget;
//
//        // If we get error on closing the stream we must return it to the caller
//        IOException streamCloseException = null;
//
//        try {
//            // Close the stream target.
//            streamTarget.close();
//        } catch (final IOException e) {
//            LOGGER.error("closeStreamTarget() - Error on closing stream {}", streamTarget, e);
//            streamCloseException = e;
//        }
//
//        updateAttribute(fileSystemStreamTarget, MetaFieldNames.RAW_SIZE,
//                String.valueOf(((FileSystemStreamTarget) streamTarget).getStreamSize()));
//
//        updateAttribute(fileSystemStreamTarget, MetaFieldNames.FILE_SIZE,
//                String.valueOf(((FileSystemStreamTarget) streamTarget).getTotalFileSize()));
//
//        try {
//            boolean doneManifest = false;
//
//            // Are we appending?
//            if (fileSystemStreamTarget.isAppend()) {
//                final Set<Path> childFile = fileSystemStreamPathHelper.createChildStreamPath(
//                        ((FileSystemStreamTarget) streamTarget).getFiles(false), InternalStreamTypeNames.MANIFEST);
//
//                // Does the manifest exist ... overwrite it
//                if (FileSystemUtil.isAllFile(childFile)) {
//                    try (final OutputStream outputStream = fileSystemStreamPathHelper.getOutputStream(InternalStreamTypeNames.MANIFEST, childFile)) {
//                        AttributeMapUtil.write(fileSystemStreamTarget.getAttributes(), outputStream);
//                    }
//                    doneManifest = true;
//                }
//            }
//
//            if (!doneManifest) {
//                // No manifest done yet ... output one if the parent dir's exist
//                if (FileSystemUtil.isAllParentDirectoryExist(((FileSystemStreamTarget) streamTarget).getFiles(false))) {
//                    try (final OutputStream outputStream = fileSystemStreamTarget.add(InternalStreamTypeNames.MANIFEST).getOutputStream()) {
//                        AttributeMapUtil.write(fileSystemStreamTarget.getAttributes(), outputStream);
//                    }
//                } else {
//                    LOGGER.warn("closeStreamTarget() - Closing target file with no directory present");
//                }
//
//            }
//        } catch (final IOException e) {
//            LOGGER.error("closeStreamTarget() - Error on writing Manifest {}", streamTarget, e);
//        }
//
//        if (streamCloseException == null) {
//            // Unlock will update the meta data so set it back on the stream
//            // target so the client has the up to date copy
//            ((FileSystemStreamTarget) streamTarget).setMetaData(
//                    unLock(streamTarget.getMeta(), fileSystemStreamTarget.getAttributes()));
//        } else {
//            throw new UncheckedIOException(streamCloseException);
//        }
//    }

    @Override
    public int deleteStreamTarget(final Target target) {
        // Make sure the stream is closed.
        try {
            target.close();
        } catch (final IOException e) {
            LOGGER.error("Unable to delete stream target!", e.getMessage(), e);
        }

        // Make sure the stream data is deleted.
        return metaService.delete(target.getMeta().getId(), false);
    }

    /**
     * <p>
     * Open a existing stream source.
     * </p>
     *
     * @param streamId the id of the stream to open.
     * @return The stream source if the stream can be found.
     * @throws DataException in case of a IO error or stream volume not visible or non
     *                         existent.
     */
    @Override
    public Source openStreamSource(final long streamId) throws DataException {
        return openStreamSource(streamId, false);
    }

    /**
     * <p>
     * Open a existing stream source.
     * </p>
     *
     * @param streamId  The stream id to open a stream source for.
     * @param anyStatus Used to specify if this method will return stream sources that
     *                  are logically deleted or locked. If false only unlocked stream
     *                  sources will be returned, null otherwise.
     * @return The loaded stream source if it exists (has not been physically
     * deleted) else null. Also returns null if one exists but is
     * logically deleted or locked unless <code>anyStatus</code> is
     * true.
     * @throws DataException Could be thrown if no volume
     */
    @Override
    public Source openStreamSource(final long streamId, final boolean anyStatus) throws DataException {
        Source streamSource = null;

        final Meta meta = metaService.getMeta(streamId, anyStatus);
        if (meta != null) {
            LOGGER.debug("openStreamSource() {}", meta.getId());

            final Set<DataVolume> volumeSet = streamVolumeService.findStreamVolume(meta.getId());
            if (volumeSet.isEmpty()) {
                final String message = "Unable to find any volume for " + meta;
                LOGGER.warn(message);
                throw new DataException(message);
            }
            final Node node = nodeInfo.getThisNode();
            final DataVolume streamVolume = streamVolumeService.pickBestVolume(volumeSet, node.getId(), node.getRack().getId());
            if (streamVolume == null) {
                final String message = "Unable to access any volume for " + meta
                        + " perhaps the data is on a private volume";
                LOGGER.warn(message);
                throw new DataException(message);
            }
            streamSource = FileSystemStreamSource.create(fileSystemStreamPathHelper, meta, streamVolume.getVolumePath(), meta.getTypeName());
        }

        return streamSource;
    }

//    @Override
//    public void closeStreamSource(final Source streamSource) {
//        try {
//            // Close the stream source.
//            streamSource.close();
//        } catch (final IOException e) {
//            LOGGER.error("Unable to close stream source!", e.getMessage(), e);
//        }
//    }
//
//    @Override
//    public AttributeMap getStoredMeta(final Meta meta) {
//        final Set<DataVolume> volumeSet = streamVolumeService.findStreamVolume(meta.getId());
//        if (volumeSet != null && volumeSet.size() > 0) {
//            final DataVolume streamVolume = volumeSet.iterator().next();
//            final Path manifest = fileSystemStreamPathHelper.createChildStreamFile(meta, streamVolume, InternalStreamTypeNames.MANIFEST);
//
//            if (Files.isRegularFile(manifest)) {
//                final AttributeMap attributeMap = new AttributeMap();
//                try {
//                    AttributeMapUtil.read(Files.newInputStream(manifest), true, attributeMap);
//                } catch (final IOException ioException) {
//                    LOGGER.error("loadAttributeMapFromFileSystem() {}", manifest, ioException);
//                }
//
////                for (final String name : attributeMap.keySet()) {
////                    final StreamAttributeKey key = keyMap.get(name);
////                    final String value = attributeMap.get(name);
////                    if (key == null) {
////                        streamAttributeMap.addAttribute(name, value);
////                    } else {
////                        streamAttributeMap.addAttribute(key, value);
////                    }
////                }
//
//                try {
//                    final Path rootFile = fileSystemStreamPathHelper.createRootStreamFile(streamVolume.getVolumePath(),
//                            meta, meta.getTypeName());
//
//                    final List<Path> allFiles = fileSystemStreamPathHelper.findAllDescendantStreamFileList(rootFile);
//                    attributeMap.put("Files", allFiles.stream().map(FileUtil::getCanonicalPath).collect(Collectors.joining(",")));
//
//
//                    //                streamAttributeMap.setFileNameList(new ArrayList<>());
//                    //                streamAttributeMap.getFileNameList().add(FileUtil.getCanonicalPath(rootFile));
//                    //                for (final Path file : allFiles) {
//                    //                    streamAttributeMap.getFileNameList().add(FileUtil.getCanonicalPath(file));
//                    //                }
//                } catch (final RuntimeException e) {
//                    LOGGER.error("loadAttributeMapFromFileSystem() ", e);
//                }
//
//                return attributeMap;
//            }
//        }
//
//        return null;
//    }

    private void syncAttributes(final Meta meta, final FileSystemStreamTarget target) {
        updateAttribute(target, MetaFieldNames.ID, String.valueOf(meta.getId()));

        if (meta.getParentMetaId() != null) {
            updateAttribute(target, MetaFieldNames.PARENT_ID,
                    String.valueOf(meta.getParentMetaId()));
        }

        updateAttribute(target, MetaFieldNames.FEED_NAME, meta.getFeedName());
        updateAttribute(target, MetaFieldNames.TYPE_NAME, meta.getTypeName());
        updateAttribute(target, MetaFieldNames.CREATE_TIME, DateUtil.createNormalDateTimeString(meta.getCreateMs()));
        if (meta.getEffectiveMs() != null) {
            updateAttribute(target, MetaFieldNames.EFFECTIVE_TIME, DateUtil.createNormalDateTimeString(meta.getEffectiveMs()));
        }
    }

    private void updateAttribute(final FileSystemStreamTarget target, final String key, final String value) {
        if (!target.getAttributes().containsKey(key)) {
            target.getAttributes().put(key, value);
        }
    }

//    private Meta unLock(final Meta meta, final AttributeMap attributeMap) {
//        if (Status.UNLOCKED.equals(meta.getStatus())) {
//            throw new IllegalStateException("Attempt to unlock data that is already unlocked");
//        }
//
//        // Write the child meta data
//        if (!attributeMap.isEmpty()) {
//            try {
//                metaService.addAttributes(meta, attributeMap);
//            } catch (final RuntimeException e) {
//                LOGGER.error("unLock() - Failed to persist attributes in new transaction... will ignore");
//            }
//        }
//
//        LOGGER.debug("unlock() " + meta);
//        return metaService.updateStatus(meta, Status.UNLOCKED, Status.LOCKED);
//    }
}
