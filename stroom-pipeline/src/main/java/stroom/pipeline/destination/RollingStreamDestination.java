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

package stroom.pipeline.destination;

import stroom.feed.MetaMap;
import stroom.streamstore.api.StreamStore;
import stroom.streamstore.api.StreamTarget;
import stroom.streamstore.fs.serializable.RASegmentOutputStream;
import stroom.streamstore.meta.api.StreamDataSource;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public class RollingStreamDestination extends RollingDestination {
    private final StreamStore streamStore;
    private final StreamTarget streamTarget;
    private final String nodeName;
    private final AtomicLong recordCount = new AtomicLong();
    private final RASegmentOutputStream segmentOutputStream;

    public RollingStreamDestination(final StreamKey key,
                                    final long frequency,
                                    final long maxSize,
                                    final long creationTime,
                                    final StreamStore streamStore,
                                    final StreamTarget streamTarget,
                                    final String nodeName) throws IOException {
        super(key, frequency, maxSize, creationTime);

        this.streamStore = streamStore;
        this.streamTarget = streamTarget;
        this.nodeName = nodeName;

        if (key.isSegmentOutput()) {
            segmentOutputStream = new RASegmentOutputStream(streamTarget);
            setOutputStream(new ByteCountOutputStream(segmentOutputStream));
        } else {
            segmentOutputStream = null;
            setOutputStream(new ByteCountOutputStream(streamTarget.getOutputStream()));
        }
    }

    @Override
    void onGetOutputStream(final Consumer<Throwable> exceptionConsumer) {
        // Insert a segment marker before we write the next record regardless of whether the header has actually
        // been written. This is because we always make an allowance for the existence of a header in a segmented
        // stream when viewing data.
        insertSegmentMarker(exceptionConsumer);

        recordCount.incrementAndGet();

        super.onGetOutputStream(exceptionConsumer);
    }

    @Override
    void beforeRoll(final Consumer<Throwable> exceptionConsumer) {
        // Writing a segment marker here ensures there is always a marker written before the footer regardless or
        // whether a footer is actually written. We do this because we always make an allowance for a footer for data
        // display purposes.
        insertSegmentMarker(exceptionConsumer);

        super.beforeRoll(exceptionConsumer);
    }

    @Override
    void afterRoll(final Consumer<Throwable> exceptionConsumer) {
        // Write meta data to stream target.
        final MetaMap metaMap = new MetaMap();
        metaMap.put(StreamDataSource.REC_WRITE, recordCount.toString());

        // TODO : @66 DO WE REALLY NEED TO KNOW WHAT NODE PROCESSED A STREAM AS THE DATA IS AVAILABLE ON STREAM TASK???
//        metaMap.put(StreamAttributeConstants.NODE, nodeName);
        streamTarget.getAttributeMap().putAll(metaMap);
        streamStore.closeStreamTarget(streamTarget);
    }

    private void insertSegmentMarker(final Consumer<Throwable> exceptionConsumer) {
        try {
            // Add a segment marker to the output stream if we are segmenting.
            if (segmentOutputStream != null) {
                segmentOutputStream.addSegment();
            }
        } catch (final IOException e) {
            exceptionConsumer.accept(e);
        }
    }
}
