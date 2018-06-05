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

package stroom.streamstore;

import stroom.entity.shared.Clearable;
import stroom.feed.MetaMap;
import stroom.io.SeekableInputStream;
import stroom.streamstore.api.StreamProperties;
import stroom.streamstore.api.StreamSource;
import stroom.streamstore.api.StreamStore;
import stroom.streamstore.api.StreamTarget;
import stroom.streamstore.fs.StreamTypeNames;
import stroom.streamstore.meta.StreamMetaService;
import stroom.streamstore.meta.db.MockStreamMetaService;
import stroom.streamstore.shared.Stream;
import stroom.streamstore.shared.StreamStatus;
import stroom.streamstore.shared.StreamTypeEntity;
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

    private Stream lastStream;


    private final StreamMetaService streamMetaService;

    @SuppressWarnings("unused")
    @Inject
    MockStreamStore(final StreamMetaService streamMetaService) {
        this.streamMetaService = streamMetaService;
    }

    public MockStreamStore() {
        this.streamMetaService = new MockStreamMetaService();
    }

////    @Override
//    public StreamEntity createStream(final StreamProperties streamProperties) {
//        final StreamTypeEntity streamType = streamTypeService.getOrCreate(streamProperties.getStreamTypeName());
//        final FeedEntity feed = feedService.getOrCreate(streamProperties.getFeedName());
//
//        final StreamEntity stream = new StreamEntity();
//
//        if (streamProperties.getParent() != null) {
//            stream.setParentStreamId(streamProperties.getParent().getId());
//        }
//
//        stream.setFeed(feed);
//        stream.setStreamType(streamType);
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
        ((MockStreamMetaService) streamMetaService).clear();
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
        openInputStream.remove(source.getStream());
    }

    @Override
    public void closeStreamTarget(final StreamTarget target) {
        // Close the stream target.
        try {
            target.close();
        } catch (final IOException e) {
            throw new StreamException(e.getMessage());
        }

        final Stream stream = target.getStream();
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
            final ByteArrayOutputStream ba = typeMap.remove(target.getStreamTypeName());
            if (ba != null && ba.toByteArray() != null) {
                dataTypeMap.put(target.getStreamTypeName(), ba.toByteArray());
            } else {
                dataTypeMap.put(target.getStreamTypeName(), new byte[0]);
            }

            // Clean up the open output streams if there are no more open types
            // for this stream.
            if (typeMap.size() == 0) {
                openOutputStream.remove(streamId);
            }
        } else {
            dataTypeMap.put(target.getStreamTypeName(), new byte[0]);
        }

        // Close child streams.
        for (final String childType : ((MockStreamTarget) target).childMap.keySet()) {
            closeStreamTarget(target.getChildStream(childType));
        }

        // Set the status of the stream to be unlocked.
        streamMetaService.updateStatus(stream.getId(), StreamStatus.UNLOCKED);
    }

//    @Override
//    public Long deleteStream(final long streamId) {
//        openInputStream.remove(streamId);
//        openOutputStream.remove(streamId);
//        fileData.remove(streamId);
//        return 1L;
//    }

    @Override
    public Long deleteStreamTarget(final StreamTarget target) {
        final long streamId = target.getStream().getId();
        openOutputStream.remove(streamId);
        fileData.remove(streamId);
        return 1L;
    }
//
//    @Override
//    public List<Stream> findEffectiveStream(final EffectiveMetaDataCriteria criteria) {
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
//                } else if (!typeMap.containsKey(criteria.getStreamType())) {
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
        final Stream stream = streamMetaService.getStream(streamId, anyStatus);
        if (stream == null) {
            return null;
        }
        openInputStream.add(streamId);
        return new MockStreamSource(stream);
    }

    @Override
    public StreamTarget openStreamTarget(final StreamProperties streamProperties) {
        final Stream stream = streamMetaService.createStream(streamProperties);
        streamMetaService.updateStatus(stream.getId(), StreamStatus.LOCKED);

        final TypedMap<String, ByteArrayOutputStream> typeMap = TypedMap.fromMap(new HashMap<>());
        typeMap.put(stream.getStreamTypeName(), new ByteArrayOutputStream());
        openOutputStream.put(stream.getId(), typeMap);

        lastStream = stream;

        return new MockStreamTarget(stream);
    }

    @Override
    public StreamTarget openExistingStreamTarget(final long streamId) throws StreamException {
        final Stream stream = streamMetaService.getStream(streamId);

        final TypedMap<String, ByteArrayOutputStream> typeMap = TypedMap.fromMap(new HashMap<>());
        typeMap.put(stream.getStreamTypeName(), new ByteArrayOutputStream());
        openOutputStream.put(stream.getId(), typeMap);

        lastStream = stream;

        return new MockStreamTarget(stream);
    }

    public Stream getLastStream() {
        return lastStream;
    }

    public TypedMap<Long, TypedMap<String, byte[]>> getFileData() {
        return fileData;
    }

//    public Map<Long, StreamEntity> getStreamMap() {
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
            final Stream stream = streamMetaService.getStream(streamId);
            sb.append(stream);
            sb.append("\n");
        }
        sb.append("\nOpen Input Streams:\n");
        for (final long streamId : openInputStream) {
            final Stream stream = streamMetaService.getStream(streamId);
            sb.append(stream);
            sb.append("\n");
        }
        sb.append("\nOpen Output Streams:\n");
        for (final long streamId : openOutputStream.keySet()) {
            final Stream stream = streamMetaService.getStream(streamId);
            sb.append(stream);
            sb.append("\n");
        }
        return sb.toString();
    }


    private Long getStreamTypeId(final StreamTypeEntity streamType) {
        if (streamType == null) {
            return null;
        }
        return streamType.getId();
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

    private class MockStreamTarget implements StreamTarget {
        private final Stream stream;
        private final String streamTypeName;
        private final MetaMap attributeMap = new MetaMap();
        private final Map<String, MockStreamTarget> childMap = new HashMap<>();
        private ByteArrayOutputStream outputStream = null;
        private StreamTarget parent;

        MockStreamTarget(final Stream stream) {
            this.stream = stream;
            this.streamTypeName = stream.getStreamTypeName();
        }

        MockStreamTarget(final StreamTarget parent, final String streamTypeName) {
            this.parent = parent;
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
        public Stream getStream() {
            return stream;
        }

        @Override
        public StreamTarget addChildStream(final String streamTypeName) {
            final TypedMap<String, ByteArrayOutputStream> typeMap = getOpenOutputStream().get(stream.getId());
            typeMap.put(streamTypeName, new ByteArrayOutputStream());
            childMap.put(streamTypeName, new MockStreamTarget(this, streamTypeName));
            return childMap.get(streamTypeName);
        }

        @Override
        public StreamTarget getChildStream(final String streamTypeName) {
            return childMap.get(streamTypeName);
        }

        @Override
        public StreamTarget getParent() {
            return parent;
        }

        @Override
        public String getStreamTypeName() {
            return streamTypeName;
        }

        @Override
        public MetaMap getAttributeMap() {
            return attributeMap;
        }

        @Override
        public boolean isAppend() {
            return false;
        }
    }

    private class MockStreamSource implements StreamSource {
        private final Stream stream;
        private final String streamTypeName;
        private final MetaMap attributeMap = new MetaMap();
        private InputStream inputStream = null;
        private StreamSource parent;

        MockStreamSource(final Stream stream) {
            this.stream = stream;
            this.streamTypeName = stream.getStreamTypeName();
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
        public Stream getStream() {
            return stream;
        }

        @Override
        public StreamSource getChildStream(final String streamTypeName) {
            final TypedMap<String, byte[]> typeMap = getFileData().get(stream.getId());
            if (typeMap.containsKey(streamTypeName)) {
                return new MockStreamSource(this, streamTypeName);
            }

            if (StreamTypeNames.BOUNDARY_INDEX.equals(streamTypeName)) {
                return new MockBoundaryStreamSource(this);
            }

            return null;
        }

        @Override
        public StreamSource getParent() {
            return parent;
        }

        @Override
        public String getStreamTypeName() {
            return streamTypeName;
        }

        @Override
        public MetaMap getAttributeMap() {
            return attributeMap;
        }
    }

    private class MockBoundaryStreamSource extends MockStreamSource {
        MockBoundaryStreamSource(final StreamSource parent) {
            super(parent, StreamTypeNames.BOUNDARY_INDEX);
        }

        @Override
        public InputStream getInputStream() {
            return new SeekableByteArrayInputStream(new byte[0]);
        }
    }
}
