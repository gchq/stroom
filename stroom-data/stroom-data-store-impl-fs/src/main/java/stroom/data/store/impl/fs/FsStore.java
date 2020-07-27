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


import stroom.data.store.api.DataException;
import stroom.data.store.api.Source;
import stroom.data.store.api.Store;
import stroom.data.store.api.Target;
import stroom.data.store.impl.fs.DataVolumeDao.DataVolume;
import stroom.data.store.impl.fs.shared.FsVolume;
import stroom.datasource.api.v2.AbstractField;
import stroom.meta.api.AttributeMapFactory;
import stroom.meta.api.MetaProperties;
import stroom.meta.api.MetaService;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaFields;
import stroom.meta.shared.Status;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

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
class FsStore implements Store, AttributeMapFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(FsStore.class);

    private final FsPathHelper fileSystemStreamPathHelper;
    private final MetaService metaService;
    private final FsVolumeService volumeService;
    private final DataVolumeService dataVolumeService;

    @Inject
    FsStore(final FsPathHelper fileSystemStreamPathHelper,
            final MetaService metaService,
            final FsVolumeService volumeService,
            final DataVolumeService dataVolumeService) {
        this.fileSystemStreamPathHelper = fileSystemStreamPathHelper;
        this.metaService = metaService;
        this.volumeService = volumeService;
        this.dataVolumeService = dataVolumeService;
    }

    @Override
    public Target openTarget(final MetaProperties metaProperties) {
        LOGGER.debug("openTarget() " + metaProperties);

        final FsVolume volume = volumeService.getVolume();
        if (volume == null) {
            throw new DataException("Failed to get lock as no writable volumes");
        }

        // First time call (no file yet exists)
        final Meta meta = metaService.create(metaProperties);

        final DataVolume dataVolume = dataVolumeService.createDataVolume(meta.getId(), volume);
        final Path volumePath = Paths.get(dataVolume.getVolumePath());
        final String streamType = meta.getTypeName();
        final FsTarget target = FsTarget.create(metaService, fileSystemStreamPathHelper, meta, volumePath, streamType, false);

        // Force Creation of the files
        target.getOutputStream();

        syncAttributes(meta, target);

        return target;
    }

    @Override
    public Target openExistingTarget(final Meta meta) throws DataException {
        Objects.requireNonNull(meta, "Null meta");
        LOGGER.debug("openExistingTarget() " + meta);

        // Lock the object
        final DataVolume dataVolume = dataVolumeService.findDataVolume(meta.getId());
        if (dataVolume == null) {
            throw new DataException("Not all volumes are unlocked");
        }
        final Meta lockedMeta = metaService.updateStatus(meta, Status.UNLOCKED, Status.LOCKED);
        final Path volumePath = Paths.get(dataVolume.getVolumePath());

        final String streamType = lockedMeta.getTypeName();
        final FsTarget target = FsTarget.create(metaService, fileSystemStreamPathHelper, lockedMeta, volumePath,
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
//                final Set<Path> childFile = fileSystemStreamPathHelper.getChildPathSet(
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
    public Target deleteTarget(final Target target) {
        // Make sure the stream is closed.
        try {
            ((FsTarget) target).delete();
        } catch (final RuntimeException e) {
            LOGGER.error("Unable to delete stream target! {}", e.getMessage(), e);
        }
        return target;
    }

    /**
     * <p>
     * Open a existing stream source.
     * </p>
     *
     * @param streamId the id of the stream to open.
     * @return The stream source if the stream can be found.
     * @throws DataException in case of a IO error or stream volume not visible or non
     *                       existent.
     */
    @Override
    public Source openSource(final long streamId) throws DataException {
        return openSource(streamId, false);
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
    public Source openSource(final long streamId, final boolean anyStatus) throws DataException {
        Source streamSource = null;

        final Meta meta = metaService.getMeta(streamId, anyStatus);
        if (meta != null) {
            LOGGER.debug("openSource() {}", meta.getId());

            final DataVolume dataVolume = dataVolumeService.findDataVolume(meta.getId());
            if (dataVolume == null) {
                final String message = "Unable to find any volume for " + meta;
                LOGGER.warn(message);
                throw new DataException(message);
            }
//            final Node node = nodeInfo.getThisNode();
//            final DataVolume streamVolume = dataVolumeService.pickBestVolume(volumeSet, node.getId(), node.getRack().getId());
//            if (streamVolume == null) {
//                final String message = "Unable to access any volume for " + meta
//                        + " perhaps the data is on a private volume";
//                LOGGER.warn(message);
//                throw new DataException(message);
//            }
            final Path volumePath = Paths.get(dataVolume.getVolumePath());
            streamSource = FsSource.create(fileSystemStreamPathHelper, meta, volumePath, meta.getTypeName());
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
//        final Set<DataVolume> volumeSet = dataVolumeService.findDataVolume(meta.getId());
//        if (volumeSet != null && volumeSet.size() > 0) {
//            final DataVolume streamVolume = volumeSet.iterator().next();
//            final Path manifest = fileSystemStreamPathHelper.getChildPath(meta, streamVolume, InternalStreamTypeNames.MANIFEST);
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
//                    final Path rootFile = fileSystemStreamPathHelper.getRootPath(streamVolume.getVolumePath(),
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

    private void syncAttributes(final Meta meta, final FsTarget target) {
        updateAttribute(target, MetaFields.ID, String.valueOf(meta.getId()));

        if (meta.getParentMetaId() != null) {
            updateAttribute(target, MetaFields.PARENT_ID,
                    String.valueOf(meta.getParentMetaId()));
        }

        updateAttribute(target, MetaFields.FEED_NAME, meta.getFeedName());
        updateAttribute(target, MetaFields.TYPE_NAME, meta.getTypeName());
        updateAttribute(target, MetaFields.CREATE_TIME, String.valueOf(meta.getCreateMs()));
        if (meta.getEffectiveMs() != null) {
            updateAttribute(target, MetaFields.EFFECTIVE_TIME, String.valueOf(meta.getEffectiveMs()));
        }
    }

    private void updateAttribute(final FsTarget target, final AbstractField key, final String value) {
        if (!target.getAttributes().containsKey(key.getName())) {
            target.getAttributes().put(key.getName(), value);
        }
    }

    @Override
    public Map<String, String> getAttributes(final Meta meta) {
        try (final Source source = openSource(meta.getId(), true)) {
            return source != null
                    ? source.getAttributes()
                    : Collections.emptyMap();
        } catch (final IOException e) {
            throw new RuntimeException(e.getMessage(), e);
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
