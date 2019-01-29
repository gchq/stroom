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

package stroom.data.store.impl.fs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.data.store.api.InputStreamProvider;
import stroom.data.store.api.NestedInputStream;
import stroom.data.store.api.Source;
import stroom.io.BasicStreamCloser;
import stroom.io.StreamCloser;
import stroom.meta.api.AttributeMapUtil;
import stroom.meta.shared.AttributeMap;
import stroom.meta.shared.Meta;
import stroom.meta.shared.Status;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A file system implementation of StreamSource.
 */
final class FileSystemStreamSource implements Source, NestedInputStreamFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileSystemStreamSource.class);

    private final FileSystemStreamPathHelper fileSystemStreamPathHelper;
    private final StreamCloser streamCloser = new BasicStreamCloser();
    private Meta meta;
    private String rootPath;
    private String streamType;
    private AttributeMap attributeMap;
    private InputStream inputStream;
    private Path file;
    private FileSystemStreamSource parent;
//    private long index;

    private FileSystemStreamSource(final FileSystemStreamPathHelper fileSystemStreamPathHelper,
                                   final Meta meta,
                                   final String rootPath,
                                   final String streamType) {
        this.fileSystemStreamPathHelper = fileSystemStreamPathHelper;
        this.meta = meta;
        this.rootPath = rootPath;
        this.streamType = streamType;

        validate();
    }

    private FileSystemStreamSource(final FileSystemStreamPathHelper fileSystemStreamPathHelper,
                                   final FileSystemStreamSource parent,
                                   final String streamType,
                                   final Path file) {
        this.fileSystemStreamPathHelper = fileSystemStreamPathHelper;
        this.meta = parent.meta;
        this.rootPath = parent.rootPath;
        this.parent = parent;
        this.streamType = streamType;
        this.file = file;
        validate();
    }

    /**
     * Creates a new file system stream source.
     *
     * @return A new file system stream source or null if a file cannot be
     * created.
     */
    static FileSystemStreamSource create(final FileSystemStreamPathHelper fileSystemStreamPathHelper,
                                         final Meta meta,
                                         final String rootPath,
                                         final String streamType) {
        return new FileSystemStreamSource(fileSystemStreamPathHelper, meta, rootPath, streamType);
    }

    @Override
    public Meta getMeta() {
        return meta;
    }

    @Override
    public AttributeMap getAttributes() {
        if (parent != null) {
            return parent.getAttributes();
        }
        if (attributeMap == null) {
            attributeMap = new AttributeMap();
            try (final FileSystemStreamSource child = child(InternalStreamTypeNames.MANIFEST)) {
                try (final InputStream inputStream = child.getInputStream()) {
                    AttributeMapUtil.read(inputStream, true, attributeMap);
                }
            } catch (final IOException e) {
                LOGGER.error("getAttributes()", e);
            }
        }
        return attributeMap;
    }

    private void validate() {
        if (streamType == null) {
            throw new IllegalStateException("Must have a stream type");
        }
    }

    @Override
    public void close() throws IOException {
        streamCloser.close();
    }

    public Path getFile() {
        if (file == null) {
            if (parent == null) {
                file = fileSystemStreamPathHelper.createRootStreamFile(rootPath, meta, streamType);
            } else {
                file = fileSystemStreamPathHelper.createChildStreamFile(parent.getFile(), streamType);
            }
        }
        return file;
    }

    @Override
    public InputStream getInputStream() {
        // First Call?
        if (inputStream == null) {
            try {
                inputStream = fileSystemStreamPathHelper.getInputStream(streamType, getFile());
                streamCloser.add(inputStream);
            } catch (IOException ioEx) {
                // Don't log this as an error if we expect this stream to have been deleted or be locked.
                if (meta == null || Status.UNLOCKED.equals(meta.getStatus())) {
                    LOGGER.error("getInputStream", ioEx);
                }

                throw new RuntimeException(ioEx);
            }
        }
        return inputStream;
    }

    //
//    @Override
    private NestedInputStream getNestedInputStream() {
//        final InputStream data = getInputStream();
//        final InputStream boundaryIndex = getChild(InternalStreamTypeNames.BOUNDARY_INDEX).getInputStream();
//        return new RANestedInputStream(data, boundaryIndex);


        return new RANestedInputStream(getInputStream(),
                () -> getChild(InternalStreamTypeNames.BOUNDARY_INDEX).getInputStream());
    }
//
//    @Override
//    public SegmentInputStream getSegmentInputStream() throws IOException {
//        final InputStream data = getInputStream();
//        final InputStream segmentIndex = getChildStream(InternalStreamTypeNames.SEGMENT_INDEX).getInputStream();
//        return new RASegmentInputStream(data, segmentIndex);
//    }
//
//    @Override
//    public CompoundInputStream getCompoundInputStream() throws IOException {
//        final InputStream data = getInputStream();
//        final InputStream boundaryIndex = getChildStream(InternalStreamTypeNames.BOUNDARY_INDEX).getInputStream();
//        final InputStream segmentIndex = getChildStream(InternalStreamTypeNames.SEGMENT_INDEX).getInputStream();
//        final RANestedInputStream nestedInputStream = new RANestedInputStream(data, boundaryIndex);
//        return new RACompoundInputStream(nestedInputStream, segmentIndex);
//    }


    @Override
    public InputStreamProvider get(final long index) {
        return new InputStreamProviderImpl(meta, this, index);
    }

    @Override
    public long count() throws IOException {
        try (final NestedInputStream nestedInputStream = getNestedInputStream()) {
            return nestedInputStream.getEntryCount();
        }
    }

//    @Override
//    public InputStreamProvider getInputStreamProvider() {
//        return new StreamSourceInputStreamProviderImpl(this);
//    }
//
//
//
//
//    @Override
//    public InputStreamProvider next() {
//        final InputStreamProvider inputStreamProvider = new InputStreamProviderImpl2(meta, this, index);
//        index++;
//        return inputStreamProvider;
//    }



    @Override
    public NestedInputStreamFactory getChild(final String streamTypeName) {
        return child(streamTypeName);
    }

    private FileSystemStreamSource child(final String streamTypeName) {
        Path childFile = fileSystemStreamPathHelper.createChildStreamFile(getFile(), streamTypeName);
        boolean lazy = fileSystemStreamPathHelper.isStreamTypeLazy(streamTypeName);
        boolean isFile = Files.isRegularFile(childFile);
        if (lazy || isFile) {
            final FileSystemStreamSource child = new FileSystemStreamSource(fileSystemStreamPathHelper, this, streamTypeName, childFile);
            streamCloser.add(child);
            return child;
        } else {
            return null;
        }
    }

//    @Override
//    public AttributeMap getAttributes() {
//        if (parent != null) {
//            return parent.getAttributes();
//        }
//        if (attributeMap == null) {
//            attributeMap = new AttributeMap();
//            try (final Source source = getChild(InternalStreamTypeNames.MANIFEST)) {
//                try (final InputStreamProvider inputStreamProvider = source.get(0)) {
//                    try (final InputStream inputStream = inputStreamProvider.get()) {
//                        AttributeMapUtil.read(inputStream, true, attributeMap);
//                    }
//                }
//            } catch (final RuntimeException | IOException e) {
//                LOGGER.error("getAttributes()", e);
//            }
//        }
//        return attributeMap;
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

    Source getParent() {
        return parent;
    }
}
