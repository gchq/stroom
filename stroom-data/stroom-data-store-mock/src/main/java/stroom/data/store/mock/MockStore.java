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

package stroom.data.store.mock;

import stroom.data.store.api.AttributeMapFactory;
import stroom.data.store.api.DataException;
import stroom.data.store.api.InputStreamProvider;
import stroom.data.store.api.OutputStreamProvider;
import stroom.data.store.api.SegmentInputStream;
import stroom.data.store.api.Source;
import stroom.data.store.api.Store;
import stroom.data.store.api.Target;
import stroom.data.store.impl.fs.InputStreamProviderImpl;
import stroom.data.store.impl.fs.InternalSource;
import stroom.data.store.impl.fs.InternalStreamTypeNames;
import stroom.data.store.impl.fs.InternalTarget;
import stroom.data.store.impl.fs.OutputStreamProviderImpl;
import stroom.data.store.impl.fs.RASegmentInputStream;
import stroom.data.store.impl.fs.SegmentInputStreamProvider;
import stroom.data.store.impl.fs.SegmentInputStreamProviderFactory;
import stroom.data.store.impl.fs.SegmentOutputStreamProvider;
import stroom.data.store.impl.fs.SegmentOutputStreamProviderFactory;
import stroom.meta.api.AttributeMap;
import stroom.meta.api.MetaProperties;
import stroom.meta.api.MetaService;
import stroom.meta.mock.MockMetaService;
import stroom.meta.shared.Meta;
import stroom.meta.shared.Status;
import stroom.util.io.SeekableInputStream;
import stroom.util.shared.Clearable;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Singleton
public class MockStore implements Store, Clearable, AttributeMapFactory {

    /**
     * Our stream data.
     */
    private final Map<Long, Map<String, byte[]>> fileData = new HashMap<>();
    private final Map<Long, Map<String, ByteArrayOutputStream>> openOutputStream = new HashMap<>();
    private final Set<Long> openInputStream = new HashSet<>();

    private Meta lastMeta;

    private final MetaService metaService;

    @SuppressWarnings("unused")
    @Inject
    public MockStore(final MetaService metaService) {
        this.metaService = metaService;
    }

    /**
     * Class this API to clear down things.
     */
    @Override
    public void clear() {
        fileData.clear();
        openOutputStream.clear();
        openInputStream.clear();
        lastMeta = null;
        ((MockMetaService) metaService).clear();
    }

    public int getStreamStoreCount() {
        return fileData.size();
    }

    @Override
    public void deleteTarget(final Target target) {
        final long streamId = target.getMeta().getId();
        openOutputStream.remove(streamId);
        fileData.remove(streamId);
        ((MockTarget) target).delete();
    }

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
        final Meta meta = metaService.getMeta(streamId, anyStatus);
        if (meta == null) {
            return null;
        }
        openInputStream.add(streamId);
        return new MockSource(meta);
    }

    @Override
    public Target openTarget(final MetaProperties metaProperties) throws DataException {
        return openTarget(metaProperties, null);
    }

    @Override
    public Target openTarget(final MetaProperties metaProperties, final String volumeGroup) {
        final Meta meta = metaService.create(metaProperties);

        final Map<String, ByteArrayOutputStream> typeMap = new HashMap<>();
        typeMap.put(meta.getTypeName(), new ByteArrayOutputStream());
        openOutputStream.put(meta.getId(), typeMap);

        lastMeta = meta;

        return new MockTarget(meta);
    }

    public Meta getLastMeta() {
        return lastMeta;
    }

    public Map<Long, Map<String, byte[]>> getFileData() {
        return fileData;
    }

    private Map<Long, Map<String, ByteArrayOutputStream>> getOpenOutputStream() {
        return openOutputStream;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("Stream Store Contains:\n");
        for (final long streamId : fileData.keySet()) {
            final Meta meta = metaService.getMeta(streamId);
            sb.append(meta);
            sb.append("\n");
        }
        sb.append("\nOpen Input Streams:\n");
        for (final long streamId : openInputStream) {
            final Meta meta = metaService.getMeta(streamId);
            sb.append(meta);
            sb.append("\n");
        }
        sb.append("\nOpen Output Streams:\n");
        for (final long streamId : openOutputStream.keySet()) {
            final Meta meta = metaService.getMeta(streamId);
            sb.append(meta);
            sb.append("\n");
        }
        return sb.toString();
    }

    @Override
    public Map<String, String> getAttributes(final long metaId) {
        return Map.of();
    }

    @Override
    public AttributeMap getAttributeMapForPart(final long streamId, final long partNo) {
        return new AttributeMap();
    }

    private static class SeekableByteArrayInputStream extends ByteArrayInputStream implements SeekableInputStream {

        SeekableByteArrayInputStream(final byte[] bytes) {
            super(bytes);
        }

        @Override
        public long getPosition() {
            return pos;
        }

        @Override
        public long getSize() {
            return buf.length;
        }

        @Override
        public void seek(final long pos) {
            this.pos = (int) pos;
        }
    }


    // --------------------------------------------------------------------------------


    private class MockTarget implements InternalTarget, SegmentOutputStreamProviderFactory {

        private final Meta meta;
        private boolean closed;
        private boolean deleted;
        private final String streamTypeName;
        private final AttributeMap attributeMap = new AttributeMap();
        private final Map<String, MockTarget> childMap = new HashMap<>();
        private final Map<String, SegmentOutputStreamProvider> outputStreamMap = new HashMap<>(10);
        private ByteArrayOutputStream outputStream = null;
        private long index;
        private Target parent;

        MockTarget(final Meta meta) {
            this.meta = meta;
            this.streamTypeName = meta.getTypeName();
        }

        MockTarget(final Target parent, final String streamTypeName) {
            this.parent = parent;
            this.meta = parent.getMeta();
            this.streamTypeName = streamTypeName;
        }

        @Override
        public Meta getMeta() {
            return meta;
        }

        @Override
        public AttributeMap getAttributes() {
            return attributeMap;
        }

        @Override
        public OutputStreamProvider next() {
            final OutputStreamProvider outputStreamProvider = new OutputStreamProviderImpl(meta, this, index);
            index++;
            return outputStreamProvider;
        }

        @Override
        public void close() {
            if (closed) {
                throw new DataException("Target already closed");
            }

            try {
                if (!deleted) {
                    // Close the stream target.
                    try {
                        if (outputStream != null) {
                            outputStream.close();
                        }
                    } catch (final IOException e) {
                        throw new DataException(e.getMessage());
                    }

                    final long streamId = meta.getId();

                    // Get the data map to add the stream output to.
                    final Map<String, byte[]> dataTypeMap = fileData.computeIfAbsent(streamId, k -> new HashMap<>());
                    final Map<String, ByteArrayOutputStream> typeMap = openOutputStream.get(streamId);

                    if (typeMap != null) {
                        // Add data from this stream to the data type map.
                        final ByteArrayOutputStream ba = typeMap.remove(streamTypeName);
                        if (ba != null && ba.toByteArray() != null) {
                            dataTypeMap.put(streamTypeName, ba.toByteArray());
                        } else {
                            dataTypeMap.put(streamTypeName, new byte[0]);
                        }

                        // Clean up the open output streams if there are no more open types
                        // for this stream.
                        if (typeMap.size() == 0) {
                            openOutputStream.remove(streamId);
                        }
                    } else {
                        dataTypeMap.put(streamTypeName, new byte[0]);
                    }

                    // Close child streams.
                    for (final String childType : childMap.keySet()) {
                        getChild(childType).close();
                    }

                    // Only unlock the meta if this is the root.
                    if (parent == null) {
                        // Set the status of the stream to be unlocked.
                        metaService.updateStatus(meta, Status.LOCKED, Status.UNLOCKED);
                    }
                }
            } finally {
                closed = true;
            }
        }

        public void delete() {
            if (deleted) {
                throw new DataException("Target already deleted");
            }
            if (closed) {
                throw new DataException("Target already closed");
            }

            try {
                // Close the stream target.
                try {
                    if (outputStream != null) {
                        outputStream.close();
                    }
                } catch (final IOException e) {
                    throw new DataException(e.getMessage());
                }

                openOutputStream.remove(meta.getId());

                // Close child streams.
                for (final String childType : childMap.keySet()) {
                    getChild(childType).close();
                }

                // Only delete the meta if this is the root.
                if (parent == null) {
                    // Set the status of the stream to be deleted.
                    metaService.updateStatus(meta, Status.LOCKED, Status.DELETED);
                }
            } finally {
                deleted = true;
            }
        }

        @Override
        public SegmentOutputStreamProvider getSegmentOutputStreamProvider(final String streamTypeName) {
            return outputStreamMap.computeIfAbsent(streamTypeName, k -> {
                final InternalTarget target = getChild(k);
                if (target == null) {
                    return null;
                }
                return new SegmentOutputStreamProvider(target, k);
            });
        }

        MockTarget getChild(final String streamTypeName) {
//            if (closed) {
//                throw new RuntimeException("Closed");
//            }

            if (streamTypeName == null) {
                return this;
            }

            return childMap.computeIfAbsent(streamTypeName, k -> {
                final Map<String, ByteArrayOutputStream> typeMap = getOpenOutputStream().get(meta.getId());
                typeMap.put(streamTypeName, new ByteArrayOutputStream());
                return new MockTarget(this, streamTypeName);
            });
        }


//        @Override
//        public NestedOutputStreamFactory addChild(final String streamTypeName) {
//            return add(streamTypeName);
//        }


        /////////////////////////////////
        // START INTERNAL TARGET
        /////////////////////////////////

        /**
         * Gets the output stream for this stream target.
         */
        @Override
        public OutputStream getOutputStream() {
            if (outputStream == null) {
                final Map<String, ByteArrayOutputStream> typeMap = getOpenOutputStream().get(meta.getId());
                outputStream = typeMap.get(streamTypeName);
            }
            return outputStream;
        }

        // TODO : WE SHOULD BE ABLE TO REMOVE THIS SOON
        @Override
        public OutputStream getChildOutputStream(final String type) {
            final InternalTarget childTarget = getChild(type);
            if (childTarget != null) {
                return childTarget.getOutputStream();
            }
            return null;
        }

        /////////////////////////////////
        // END INTERNAL TARGET
        /////////////////////////////////

//        MockStreamTarget add(final String streamTypeName) {
//            final Map<String, ByteArrayOutputStream> typeMap = getOpenOutputStream().get(meta.getId());
//            typeMap.put(streamTypeName, new ByteArrayOutputStream());
//            childMap.put(streamTypeName, new MockStreamTarget(this, streamTypeName));
//            return childMap.get(streamTypeName);
//        }
//
//        Target getChildStream(final String streamTypeName) {
//            return childMap.get(streamTypeName);
//        }
    }


    // --------------------------------------------------------------------------------


    private class MockSource implements InternalSource, SegmentInputStreamProviderFactory {

        private final Map<String, MockSource> childMap = new HashMap<>();
        private final Map<String, SegmentInputStreamProvider> inputStreamMap = new HashMap<>(10);
        private final String streamType;
        private final Source parent;
        private AttributeMap attributeMap;
        private InputStream inputStream;

        private final Meta meta;
        private boolean closed;

        MockSource(final Meta meta) {
            this.parent = null;
            this.meta = meta;
            this.streamType = meta.getTypeName();
        }

        MockSource(final Source parent, final String streamTypeName) {
            this.parent = parent;
            this.meta = parent.getMeta();
            this.streamType = streamTypeName;
        }

        @Override
        public Meta getMeta() {
            return meta;
        }

        @Override
        public AttributeMap getAttributes() {
            return attributeMap;
        }

        @Override
        public InputStreamProvider get(final long index) {
            return new InputStreamProviderImpl(meta, this, index);
        }

        @Override
        public Set<String> getChildTypes() {
            throw new UnsupportedOperationException("TODO");
        }

        @Override
        public long count() throws IOException {
            final InputStream data = getInputStream();
            final InputStream boundaryIndex = getChildInputStream(InternalStreamTypeNames.BOUNDARY_INDEX);

            try (final SegmentInputStream segmentInputStream = new RASegmentInputStream(data, boundaryIndex)) {
                return segmentInputStream.count();
            }
        }

        @Override
        public long count(final String childStreamType) throws IOException {
            final InputStream data = getChildInputStream(childStreamType);
            final InputStream boundaryIndex = getChildInputStream(InternalStreamTypeNames.BOUNDARY_INDEX);

            try (final SegmentInputStream segmentInputStream = new RASegmentInputStream(data, boundaryIndex)) {
                return segmentInputStream.count();
            }
        }

        /////////////////////////////////
        // START INTERNAL SOURCE
        /////////////////////////////////
        @Override
        public InputStream getInputStream() {
            if (inputStream == null) {
                final Map<String, byte[]> typeMap = getFileData().get(meta.getId());
                final byte[] data = typeMap.get(streamType);

                if (data == null) {
                    throw new IllegalStateException("Some how we have null data stream in the stream store");
                }
                inputStream = new SeekableByteArrayInputStream(data);
            }
            return inputStream;
        }

        // TODO : WE SHOULD BE ABLE TO REMOVE THIS SOON
        @Override
        public InputStream getChildInputStream(final String type) {
            final InternalSource childSource = getChild(type);
            if (childSource != null) {
                return childSource.getInputStream();
            }
            return null;
        }

        /////////////////////////////////
        // END INTERNAL SOURCE
        /////////////////////////////////

        @Override
        public SegmentInputStreamProvider getSegmentInputStreamProvider(final String streamTypeName) {
            return inputStreamMap.computeIfAbsent(streamTypeName, k -> {
                final InternalSource source = getChild(k);
                if (source == null) {
                    return null;
                }
                return new SegmentInputStreamProvider(source, k);
            });
        }

        private InternalSource getChild(final String streamTypeName) {
            if (closed) {
                throw new RuntimeException("Closed");
            }

            if (streamTypeName == null) {
                return this;
            }

            return childMap.computeIfAbsent(streamTypeName, this::child);
        }

        private MockSource child(final String streamTypeName) {
            final Map<String, byte[]> typeMap = getFileData().get(meta.getId());
            if (typeMap.containsKey(streamTypeName)) {
                return new MockSource(this, streamTypeName);
            }

            if (InternalStreamTypeNames.BOUNDARY_INDEX.equals(streamTypeName)) {
                return new MockBoundaryStreamSource(this);
            }

            return null;
        }

        /**
         * Close off the stream.
         */
        @Override
        public void close() {
            if (closed) {
                throw new DataException("Source already closed");
            }

            try {
                if (inputStream != null) {
                    inputStream.close();
                }
            } catch (final IOException e) {
                throw new DataException(e.getMessage());
            } finally {
                if (parent == null) {
                    openInputStream.remove(meta.getId());
                }
                closed = true;
            }
        }
    }

    private class MockBoundaryStreamSource extends MockSource {

        MockBoundaryStreamSource(final Source parent) {
            super(parent, InternalStreamTypeNames.BOUNDARY_INDEX);
        }

        @Override
        public InputStream getInputStream() {
            return new SeekableByteArrayInputStream(new byte[0]);
        }
    }

//    private static class MockSegmentOutputStreamProvider implements SegmentOutputStreamProvider {
//        private static Logger LOGGER = LoggerFactory.getLogger(MockSegmentOutputStreamProvider.class);
//
//        private long index = -1;
//        private final String dataTypeName;
//        private final RANestedOutputStream nestedOutputStream;
//        private final SegmentOutputStream outputStream;
//
//        MockSegmentOutputStreamProvider(final MockStreamTarget target, final String dataTypeName) {
//            this.dataTypeName = dataTypeName;
//
//            nestedOutputStream = new RANestedOutputStream(target.getOutputStream(),
//                    () -> target.get(InternalStreamTypeNames.BOUNDARY_INDEX).getOutputStream());
//            outputStream = new RASegmentOutputStream(nestedOutputStream,
//                    () -> target.get(InternalStreamTypeNames.SEGMENT_INDEX).getOutputStream());
//        }
//
//        public SegmentOutputStream get(final long index) {
//            try {
//                if (this.index >= index) {
//                    throw new IOException("Output stream already provided for index " + index);
//                }
//
//                // Move up to the right index if this OS is behind, i.e. it hasn't been requested
//                for a certain data type before.
//                while (this.index < index - 1) {
//                    LOGGER.debug("Fast forwarding for " + dataTypeName);
//                    this.index++;
//                    nestedOutputStream.putNextEntry();
//                    nestedOutputStream.closeEntry();
//                }
//
//                this.index++;
//                nestedOutputStream.putNextEntry();
//
//                return new WrappedSegmentOutputStream(outputStream) {
//                    @Override
//                    public void close() throws IOException {
//                        nestedOutputStream.closeEntry();
//                    }
//                };
//            } catch (final IOException e) {
//                throw new UncheckedIOException(e);
//            }
//        }
//    }
}
