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
import stroom.feed.AttributeMap;
import stroom.node.NodeCache;
import stroom.node.VolumeService;
import stroom.node.shared.Node;
import stroom.node.shared.VolumeEntity;
import stroom.data.store.StreamException;
import stroom.data.store.api.StreamSource;
import stroom.data.store.api.StreamStore;
import stroom.data.store.api.StreamTarget;
import stroom.data.store.impl.fs.StreamVolumeService.StreamVolume;
import stroom.data.meta.api.Stream;
import stroom.data.meta.api.StreamMetaService;
import stroom.data.meta.api.StreamProperties;
import stroom.data.meta.api.StreamStatus;
import stroom.data.meta.api.StreamDataSource;
import stroom.streamstore.shared.StreamTypeNames;
import stroom.util.io.FileUtil;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
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
public class FileSystemStreamStoreImpl implements StreamStore {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileSystemStreamStoreImpl.class);

    private final FileSystemStreamPathHelper fileSystemStreamPathHelper;
    private final StreamMetaService streamMetaService;
    private final NodeCache nodeCache;
    private final VolumeService volumeService;
    private final StreamVolumeService streamVolumeService;

    @Inject
    FileSystemStreamStoreImpl(final FileSystemStreamPathHelper fileSystemStreamPathHelper,
                              final StreamMetaService streamMetaService,
                              final NodeCache nodeCache,
                              final VolumeService volumeService,
                              final StreamVolumeService streamVolumeService) {
        this.fileSystemStreamPathHelper = fileSystemStreamPathHelper;
        this.streamMetaService = streamMetaService;
        this.nodeCache = nodeCache;
        this.volumeService = volumeService;
        this.streamVolumeService = streamVolumeService;
    }

    @Override
    public StreamTarget openStreamTarget(final StreamProperties streamProperties) {
        LOGGER.debug("openStreamTarget() " + streamProperties);

        final Set<VolumeEntity> volumeSet = volumeService.getStreamVolumeSet(nodeCache.getDefaultNode());
        if (volumeSet.isEmpty()) {
            throw new StreamException("Failed to get lock as no writeable volumes");
        }

        // First time call (no file yet exists)
        final Stream stream = streamMetaService.createStream(streamProperties);

        final Set<StreamVolume> streamVolumes = streamVolumeService.createStreamVolumes(stream.getId(), volumeSet);
        final Set<String> rootPaths = streamVolumes.stream().map(StreamVolume::getVolumePath).collect(Collectors.toSet());
        final String streamType = stream.getStreamTypeName();
        final FileSystemStreamTarget target = FileSystemStreamTarget.create(fileSystemStreamPathHelper, stream, rootPaths, streamType, false);

        // Force Creation of the files
        target.getOutputStream();

        syncAttributes(stream, stream, target);

        return target;
    }

    @Override
    public StreamTarget openExistingStreamTarget(final long streamId) throws StreamException {
        LOGGER.debug("openExistingStreamTarget() " + streamId);

        // Lock the object
        final Set<StreamVolume> streamVolumes = streamVolumeService.findStreamVolume(streamId);
        if (streamVolumes.isEmpty()) {
            throw new StreamException("Not all volumes are unlocked");
        }
        final Stream stream = streamMetaService.updateStatus(streamId, StreamStatus.LOCKED);
        final Set<String> rootPaths = streamVolumes.stream().map(StreamVolume::getVolumePath).collect(Collectors.toSet());

        final String streamType = stream.getStreamTypeName();
        final FileSystemStreamTarget target = FileSystemStreamTarget.create(fileSystemStreamPathHelper, stream, rootPaths,
                streamType, true);

        syncAttributes(stream, stream, target);

        return target;
    }

    @Override
    public void closeStreamTarget(final StreamTarget streamTarget) {
        // If we get error on closing the stream we must return it to the caller
        IOException streamCloseException = null;

        try {
            // Close the stream target.
            streamTarget.close();
        } catch (final IOException e) {
            LOGGER.error("closeStreamTarget() - Error on closing stream {}", streamTarget, e);
            streamCloseException = e;
        }

        updateAttribute(streamTarget, StreamDataSource.STREAM_SIZE,
                String.valueOf(((FileSystemStreamTarget) streamTarget).getStreamSize()));

        updateAttribute(streamTarget, StreamDataSource.FILE_SIZE,
                String.valueOf(((FileSystemStreamTarget) streamTarget).getTotalFileSize()));

        try {
            boolean doneManifest = false;

            // Are we appending?
            if (streamTarget.isAppend()) {
                final Set<Path> childFile = fileSystemStreamPathHelper.createChildStreamPath(
                        ((FileSystemStreamTarget) streamTarget).getFiles(false), StreamTypeNames.MANIFEST);

                // Does the manifest exist ... overwrite it
                if (FileSystemUtil.isAllFile(childFile)) {
                    streamTarget.getAttributeMap()
                            .write(fileSystemStreamPathHelper.getOutputStream(StreamTypeNames.MANIFEST, childFile), true);
                    doneManifest = true;
                }
            }

            if (!doneManifest) {
                // No manifest done yet ... output one if the parent dir's exist
                if (FileSystemUtil.isAllParentDirectoryExist(((FileSystemStreamTarget) streamTarget).getFiles(false))) {
                    streamTarget.getAttributeMap()
                            .write(streamTarget.addChildStream(StreamTypeNames.MANIFEST).getOutputStream(), true);
                } else {
                    LOGGER.warn("closeStreamTarget() - Closing target file with no directory present");
                }

            }
        } catch (final IOException e) {
            LOGGER.error("closeStreamTarget() - Error on writing Manifest {}", streamTarget, e);
        }

        if (streamCloseException == null) {
            // Unlock will update the meta data so set it back on the stream
            // target so the client has the up to date copy
            ((FileSystemStreamTarget) streamTarget).setMetaData(
                    unLock(streamTarget.getStream(), streamTarget.getAttributeMap()));
        } else {
            throw new UncheckedIOException(streamCloseException);
        }
    }

    @Override
    public int deleteStreamTarget(final StreamTarget target) {
        // Make sure the stream is closed.
        try {
            target.close();
        } catch (final IOException e) {
            LOGGER.error("Unable to delete stream target!", e.getMessage(), e);
        }

        // Make sure the stream data is deleted.
        return streamMetaService.deleteStream(target.getStream().getId(), false);
    }

    /**
     * <p>
     * Open a existing stream source.
     * </p>
     *
     * @param streamId the id of the stream to open.
     * @return The stream source if the stream can be found.
     * @throws StreamException in case of a IO error or stream volume not visible or non
     *                         existent.
     */
    @Override
    public StreamSource openStreamSource(final long streamId) throws StreamException {
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
     * @throws StreamException Could be thrown if no volume
     */
    @Override
    public StreamSource openStreamSource(final long streamId, final boolean anyStatus) throws StreamException {
        StreamSource streamSource = null;

        final Stream stream = streamMetaService.getStream(streamId, anyStatus);
        if (stream != null) {
            LOGGER.debug("openStreamSource() {}", stream.getId());

            final Set<StreamVolume> volumeSet = streamVolumeService.findStreamVolume(stream.getId());
            if (volumeSet.isEmpty()) {
                final String message = "Unable to find any volume for " + stream;
                LOGGER.warn(message);
                throw new StreamException(message);
            }
            final Node node = nodeCache.getDefaultNode();
            final StreamVolume streamVolume = streamVolumeService.pickBestVolume(volumeSet, node.getId(), node.getRack().getId());
            if (streamVolume == null) {
                final String message = "Unable to access any volume for " + stream
                        + " perhaps the stream is on a private volume";
                LOGGER.warn(message);
                throw new StreamException(message);
            }
            streamSource = FileSystemStreamSource.create(fileSystemStreamPathHelper, stream, streamVolume.getVolumePath(), stream.getStreamTypeName());
        }

        return streamSource;
    }

    @Override
    public void closeStreamSource(final StreamSource streamSource) {
        try {
            // Close the stream source.
            streamSource.close();
        } catch (final IOException e) {
            LOGGER.error("Unable to close stream source!", e.getMessage(), e);
        }
    }

    @Override
    public Map<String, String> getStoredMeta(final Stream stream) {
        final Set<StreamVolume> volumeSet = streamVolumeService.findStreamVolume(stream.getId());
        if (volumeSet != null && volumeSet.size() > 0) {
            final StreamVolume streamVolume = volumeSet.iterator().next();
            final Path manifest = fileSystemStreamPathHelper.createChildStreamFile(stream, streamVolume, StreamTypeNames.MANIFEST);

            if (Files.isRegularFile(manifest)) {
                final AttributeMap attributeMap = new AttributeMap();
                try {
                    attributeMap.read(Files.newInputStream(manifest), true);
                } catch (final IOException ioException) {
                    LOGGER.error("loadAttributeMapFromFileSystem() {}", manifest, ioException);
                }

//                for (final String name : attributeMap.keySet()) {
//                    final StreamAttributeKey key = keyMap.get(name);
//                    final String value = attributeMap.get(name);
//                    if (key == null) {
//                        streamAttributeMap.addAttribute(name, value);
//                    } else {
//                        streamAttributeMap.addAttribute(key, value);
//                    }
//                }

                try {
                    final Path rootFile = fileSystemStreamPathHelper.createRootStreamFile(streamVolume.getVolumePath(),
                            stream, stream.getStreamTypeName());

                    final List<Path> allFiles = fileSystemStreamPathHelper.findAllDescendantStreamFileList(rootFile);
                    attributeMap.put("Files", allFiles.stream().map(FileUtil::getCanonicalPath).collect(Collectors.joining(",")));


                    //                streamAttributeMap.setFileNameList(new ArrayList<>());
                    //                streamAttributeMap.getFileNameList().add(FileUtil.getCanonicalPath(rootFile));
                    //                for (final Path file : allFiles) {
                    //                    streamAttributeMap.getFileNameList().add(FileUtil.getCanonicalPath(file));
                    //                }
                } catch (final RuntimeException e) {
                    LOGGER.error("loadAttributeMapFromFileSystem() ", e);
                }

                return attributeMap;
            }
        }

        return null;
    }

    private void syncAttributes(final Stream stream, final Stream dbStream, final FileSystemStreamTarget target) {
        updateAttribute(target, StreamDataSource.STREAM_ID, String.valueOf(dbStream.getId()));

        if (dbStream.getParentStreamId() != null) {
            updateAttribute(target, StreamDataSource.PARENT_STREAM_ID,
                    String.valueOf(dbStream.getParentStreamId()));
        }

        updateAttribute(target, StreamDataSource.FEED, dbStream.getFeedName());
        updateAttribute(target, StreamDataSource.STREAM_TYPE, dbStream.getStreamTypeName());
        updateAttribute(target, StreamDataSource.CREATE_TIME, DateUtil.createNormalDateTimeString(stream.getCreateMs()));
        if (stream.getEffectiveMs() != null) {
            updateAttribute(target, StreamDataSource.EFFECTIVE_TIME, DateUtil.createNormalDateTimeString(stream.getEffectiveMs()));
        }
    }

    private void updateAttribute(final StreamTarget target, final String key, final String value) {
        if (!target.getAttributeMap().containsKey(key)) {
            target.getAttributeMap().put(key, value);
        }
    }

    private Stream unLock(final Stream stream, final Map<String, String> attributeMap) {
        if (StreamStatus.UNLOCKED.equals(stream.getStatus())) {
            throw new IllegalStateException("Attempt to unlock a stream that is already unlocked");
        }

        // Write the child meta data
        if (!attributeMap.isEmpty()) {
            try {
                streamMetaService.addAttributes(stream, attributeMap);
            } catch (final RuntimeException e) {
                LOGGER.error("unLock() - Failed to persist attributes in new transaction... will ignore");
            }
        }

        LOGGER.debug("unlock() " + stream);
        return streamMetaService.updateStatus(stream.getId(), StreamStatus.UNLOCKED);
    }
}
