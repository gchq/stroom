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

import stroom.data.meta.api.AttributeMap;
import stroom.data.meta.api.Data;
import stroom.data.meta.api.DataMetaService;
import stroom.data.meta.api.DataProperties;
import stroom.data.meta.api.DataStatus;
import stroom.data.meta.impl.mock.MockDataMetaService;
import stroom.data.store.api.CompoundInputStream;
import stroom.data.store.api.NestedInputStream;
import stroom.data.store.api.OutputStreamProvider;
import stroom.data.store.api.SegmentInputStream;
import stroom.data.store.api.StreamException;
import stroom.data.store.api.StreamSource;
import stroom.data.store.api.StreamSourceInputStreamProvider;
import stroom.data.store.api.StreamStore;
import stroom.data.store.api.StreamTarget;
import stroom.entity.shared.Clearable;
import stroom.io.SeekableInputStream;
import stroom.util.collections.TypedMap;

import javax.inject.Inject;
import javax.inject.Singleton;
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
public class MockStreamStore implements StreamStore, Clearable {
    /**
     * Our stream data.
     */
    private final TypedMap<Long, TypedMap<String, byte[]>> fileData = TypedMap.fromMap(new HashMap<>());
    private final TypedMap<Long, TypedMap<String, ByteArrayOutputStream>> openOutputStream = TypedMap
            .fromMap(new HashMap<>());
    private final Set<Long> openInputStream = new HashSet<>();

    private Data lastStream;

    private final DataMetaService streamMetaService;

    @SuppressWarnings("unused")
    @Inject
    MockStreamStore(final DataMetaService streamMetaService) {
        this.streamMetaService = streamMetaService;
    }

//    public MockStreamStore() {
//        this.streamMetaService = new MockStreamMetaService();
//    }

////    @Override
//    public StreamEntity create(final StreamProperties streamProperties) {
//        final StreamTypeEntity streamType = streamTypeService.getOrCreate(streamProperties.getTypeName());
//        final FeedEntity feed = feedService.getOrCreate(streamProperties.getFeedName());
//
//        final StreamEntity stream = new StreamEntity();
//
//        if (streamProperties.getParent() != null) {
//            stream.setParentStreamId(streamProperties.getParent().getId());
//        }
//
//        stream.setFeed(feed);
//        stream.setType(streamType);
//        stream.setStreamProcessor(streamProperties.getStreamProcessor());
//        if (streamProperties.getStreamTask() != null) {
//            stream.setStreamTaskId(streamProperties.getStreamTask().getId());
//        }
//        stream.setCreateMs(streamProperties.getCreateMs());
//        stream.setEffectiveMs(streamProperties.getEffectiveMs());
//        stream.setStatusMs(streamProperties.getStatusMs());
//
//        return stream;
//    }

//    /**
//     * Load a stream by id.
//     *
//     * @param id The stream id to load a stream for.
//     * @return The loaded stream if it exists (has not been physically deleted)
//     * and is not logically deleted or locked, null otherwise.
//     */
//    @Override
//    public StreamEntity loadStreamById(final long id) {
//        return streamMap.get(id);
//    }
//
//    /**
//     * Load a stream by id.
//     *
//     * @param id        The stream id to load a stream for.
//     * @param anyStatus Used to specify if this method will return streams that are
//     *                  logically deleted or locked. If false only unlocked streams
//     *                  will be returned, null otherwise.
//     * @return The loaded stream if it exists (has not been physically deleted)
//     * else null. Also returns null if one exists but is logically
//     * deleted or locked unless <code>anyStatus</code> is true.
//     */
//    @Override
//    public StreamEntity loadStreamById(final long id, final boolean anyStatus) {
//        return loadStreamById(id);
//    }

    /**
     * Class this API to clear down things.
     */
    @Override
    public void clear() {
        fileData.clear();
        openOutputStream.clear();
        openInputStream.clear();
        ((MockDataMetaService) streamMetaService).clear();
    }

    public int getStreamStoreCount() {
        return fileData.size();
    }

    @Override
    public void closeStreamSource(final StreamSource source) {
        // Close the stream source.
        try {
            source.close();
        } catch (final IOException e) {
            throw new StreamException(e.getMessage());
        }
        openInputStream.remove(source.getStream().getId());
    }

    @Override
    public void closeStreamTarget(final StreamTarget target) {
        final MockStreamTarget mockStreamTarget = (MockStreamTarget) target;
        final String streamTypeName = mockStreamTarget.getStreamTypeName();

        // Close the stream target.
        try {
            target.close();
        } catch (final IOException e) {
            throw new StreamException(e.getMessage());
        }

        final Data stream = target.getStream();
        final long streamId = stream.getId();

        // Get the data map to add the stream output to.
        TypedMap<String, byte[]> dataTypeMap = fileData.get(streamId);
        if (dataTypeMap == null) {
            dataTypeMap = TypedMap.fromMap(new HashMap<>());
            fileData.put(stream.getId(), dataTypeMap);
        }

        final TypedMap<String, ByteArrayOutputStream> typeMap = openOutputStream.get(streamId);

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
        for (final String childType : mockStreamTarget.childMap.keySet()) {
            closeStreamTarget(mockStreamTarget.getChildStream(childType));
        }

        // Set the status of the stream to be unlocked.
        streamMetaService.updateStatus(stream, DataStatus.UNLOCKED);
    }

//    @Override
//    public Long delete(final long streamId) {
//        openInputStream.remove(streamId);
//        openOutputStream.remove(streamId);
//        fileData.remove(streamId);
//        return 1L;
//    }

    @Override
    public int deleteStreamTarget(final StreamTarget target) {
        final long streamId = target.getStream().getId();
        openOutputStream.remove(streamId);
        fileData.remove(streamId);
        return 1;
    }
//
//    @Override
//    public List<Stream> findEffectiveData(final EffectiveMetaDataCriteria criteria) {
//        final ArrayList<Stream> results = new ArrayList<>();
//
//        try {
//            for (final long streamId : fileData.keySet()) {
//                final TypedMap<String, byte[]> typeMap = fileData.get(streamId);
//                final StreamEntity stream = streamMap.get(streamId);
//
//                boolean match = true;
//
//                if (typeMap == null) {
//                    match = false;
//                } else if (!typeMap.containsKey(criteria.getType())) {
//                    match = false;
//                } else if (!criteria.getFeed().equals(stream.getFeedName())) {
//                    match = false;
//                }
//
//                if (match) {
//                    results.add(stream);
//                }
//            }
//        } catch (final RuntimeException e) {
//            System.out.println(e.getMessage());
//            // Ignore ... just a mock
//        }
//
//        return BaseResultList.createUnboundedList(results);
//    }

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
        final Data stream = streamMetaService.getData(streamId, anyStatus);
        if (stream == null) {
            return null;
        }
        openInputStream.add(streamId);
        return new MockStreamSource(stream);
    }

    @Override
    public StreamTarget openStreamTarget(final DataProperties streamProperties) {
        final Data stream = streamMetaService.create(streamProperties);

        final TypedMap<String, ByteArrayOutputStream> typeMap = TypedMap.fromMap(new HashMap<>());
        typeMap.put(stream.getTypeName(), new ByteArrayOutputStream());
        openOutputStream.put(stream.getId(), typeMap);

        lastStream = stream;

        return new MockStreamTarget(stream);
    }

    @Override
    public StreamTarget openExistingStreamTarget(final Data stream) throws StreamException {
        final TypedMap<String, ByteArrayOutputStream> typeMap = TypedMap.fromMap(new HashMap<>());
        typeMap.put(stream.getTypeName(), new ByteArrayOutputStream());
        openOutputStream.put(stream.getId(), typeMap);

        lastStream = stream;

        return new MockStreamTarget(stream);
    }

    @Override
    public AttributeMap getStoredMeta(final Data stream) {
        return null;
    }

    public Data getLastStream() {
        return lastStream;
    }

    public TypedMap<Long, TypedMap<String, byte[]>> getFileData() {
        return fileData;
    }

//    public Map<Long, StreamEntity> getDataMap() {
//        return streamMap;
//    }

    private TypedMap<Long, TypedMap<String, ByteArrayOutputStream>> getOpenOutputStream() {
        return openOutputStream;
    }

//    @Override
//    public long getLockCount() {
//        return openInputStream.size() + openOutputStream.size();
//    }
//
//
//
//    /**
//     * Overridden.
//     *
//     * @param findStreamCriteria NA
//     * @return NA
//     */
//    @Override
//    public BaseResultList<StreamEntity> find(final OldFindStreamCriteria findStreamCriteria) {
//        final List<StreamEntity> list = new ArrayList<>();
//        for (final long streamId : fileData.keySet()) {
//            final StreamEntity stream = streamMap.get(streamId);
//            if (findStreamCriteria.isMatch(stream)) {
//                list.add(stream);
//            }
//        }
//
//        return BaseResultList.createUnboundedList(list);
//    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("Stream Store Contains:\n");
        for (final long streamId : fileData.keySet()) {
            final Data stream = streamMetaService.getData(streamId);
            sb.append(stream);
            sb.append("\n");
        }
        sb.append("\nOpen Input Streams:\n");
        for (final long streamId : openInputStream) {
            final Data stream = streamMetaService.getData(streamId);
            sb.append(stream);
            sb.append("\n");
        }
        sb.append("\nOpen Output Streams:\n");
        for (final long streamId : openOutputStream.keySet()) {
            final Data stream = streamMetaService.getData(streamId);
            sb.append(stream);
            sb.append("\n");
        }
        return sb.toString();
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

    private class MockStreamTarget implements StreamTarget, NestedOutputStreamFactory {
        private final Data stream;
        private final String streamTypeName;
        private final AttributeMap attributeMap = new AttributeMap();
        private final Map<String, MockStreamTarget> childMap = new HashMap<>();
        private ByteArrayOutputStream outputStream = null;
//        private StreamTarget parent;

        MockStreamTarget(final Data stream) {
            this.stream = stream;
            this.streamTypeName = stream.getTypeName();
        }

        MockStreamTarget(final StreamTarget parent, final String streamTypeName) {
//            this.parent = parent;
            this.stream = parent.getStream();
            this.streamTypeName = streamTypeName;
        }

        @Override
        public OutputStream getOutputStream() {
            if (outputStream == null) {
                final TypedMap<String, ByteArrayOutputStream> typeMap = getOpenOutputStream().get(stream.getId());
                outputStream = typeMap.get(streamTypeName);
            }
            return outputStream;
        }

        @Override
        public OutputStreamProvider getOutputStreamProvider() {
            return new OutputStreamProviderImpl(stream, this);
        }

//        @Override
//        public SegmentOutputStream getSegmentOutputStream() {
//            return new RASegmentOutputStream(getOutputStream(),
//                    () -> add(InternalStreamTypeNames.SEGMENT_INDEX).getOutputStream());
//        }

        @Override
        public void close() {
            try {
                if (outputStream != null) {
                    outputStream.close();
                }
            } catch (final IOException ioEx) {
                // Wrap it
                throw new RuntimeException(ioEx);
            }
        }

        @Override
        public Data getStream() {
            return stream;
        }

//        @Override
//        public StreamTarget addChildStream(final String typeName) {
//            return add(typeName);
//        }

        @Override
        public NestedOutputStreamFactory addChild(final String streamTypeName) {
            return add(streamTypeName);
        }

        MockStreamTarget add(final String streamTypeName) {
            final TypedMap<String, ByteArrayOutputStream> typeMap = getOpenOutputStream().get(stream.getId());
            typeMap.put(streamTypeName, new ByteArrayOutputStream());
            childMap.put(streamTypeName, new MockStreamTarget(this, streamTypeName));
            return childMap.get(streamTypeName);
        }

        StreamTarget getChildStream(final String streamTypeName) {
            return childMap.get(streamTypeName);
        }

//        StreamTarget getParent() {
//            return parent;
//        }

        String getStreamTypeName() {
            return streamTypeName;
        }

        @Override
        public AttributeMap getAttributes() {
            return attributeMap;
        }

//        @Override
//        public boolean isAppend() {
//            return false;
//        }
    }

    private class MockStreamSource implements StreamSource {
        private final Data stream;
        private final String streamTypeName;
        private final AttributeMap attributeMap = new AttributeMap();
        private InputStream inputStream = null;
        private StreamSource parent;

        MockStreamSource(final Data stream) {
            this.stream = stream;
            this.streamTypeName = stream.getTypeName();
        }

        MockStreamSource(final StreamSource parent, final String streamTypeName) {
            this.parent = parent;
            this.stream = parent.getStream();
            this.streamTypeName = streamTypeName;
        }

        @Override
        public InputStream getInputStream() {
            if (inputStream == null) {
                final TypedMap<String, byte[]> typeMap = getFileData().get(stream.getId());
                final byte[] data = typeMap.get(streamTypeName);

                if (data == null) {
                    throw new IllegalStateException("Some how we have null data stream in the stream store");
                }
                inputStream = new SeekableByteArrayInputStream(data);
            }
            return inputStream;
        }

        @Override
        public NestedInputStream getNestedInputStream() throws IOException {
            final InputStream data = getInputStream();
            final InputStream boundaryIndex = getChildStream(InternalStreamTypeNames.BOUNDARY_INDEX).getInputStream();
            return new RANestedInputStream(data, boundaryIndex);
        }

        @Override
        public SegmentInputStream getSegmentInputStream() throws IOException {
            final InputStream data = getInputStream();
            final InputStream segmentIndex = getChildStream(InternalStreamTypeNames.SEGMENT_INDEX).getInputStream();
            return new RASegmentInputStream(data, segmentIndex);
        }

        @Override
        public CompoundInputStream getCompoundInputStream() throws IOException {
            final InputStream data = getInputStream();
            final InputStream boundaryIndex = getChildStream(InternalStreamTypeNames.BOUNDARY_INDEX).getInputStream();
            final InputStream segmentIndex = getChildStream(InternalStreamTypeNames.SEGMENT_INDEX).getInputStream();
            final RANestedInputStream nestedInputStream = new RANestedInputStream(data, boundaryIndex);
            return new RACompoundInputStream(nestedInputStream, segmentIndex);
        }

        @Override
        public StreamSourceInputStreamProvider getInputStreamProvider() {
            return new StreamSourceInputStreamProviderImpl(this);
        }

        /**
         * Close off the stream.
         */
        @Override
        public void close() {
            try {
                if (inputStream != null) {
                    inputStream.close();
                }
            } catch (final IOException ioEx) {
                // Wrap it
                throw new RuntimeException(ioEx);
            }
        }

        @Override
        public Data getStream() {
            return stream;
        }

        @Override
        public StreamSource getChildStream(final String streamTypeName) {
            final TypedMap<String, byte[]> typeMap = getFileData().get(stream.getId());
            if (typeMap.containsKey(streamTypeName)) {
                return new MockStreamSource(this, streamTypeName);
            }

            if (InternalStreamTypeNames.BOUNDARY_INDEX.equals(streamTypeName)) {
                return new MockBoundaryStreamSource(this);
            }

            return null;
        }

        StreamSource getParent() {
            return parent;
        }

        @Override
        public String getStreamTypeName() {
            return streamTypeName;
        }

        @Override
        public AttributeMap getAttributes() {
            return attributeMap;
        }
    }

    private class MockBoundaryStreamSource extends MockStreamSource {
        MockBoundaryStreamSource(final StreamSource parent) {
            super(parent, InternalStreamTypeNames.BOUNDARY_INDEX);
        }

        @Override
        public InputStream getInputStream() {
            return new SeekableByteArrayInputStream(new byte[0]);
        }
    }
}
