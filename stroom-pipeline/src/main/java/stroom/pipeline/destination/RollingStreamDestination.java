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

import stroom.meta.shared.MetaFieldNames;
import stroom.data.store.api.SegmentOutputStream;
import stroom.data.store.api.Store;
import stroom.data.store.api.Target;
import stroom.util.io.ByteCountOutputStream;
import stroom.util.scheduler.SimpleCron;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public class RollingStreamDestination extends RollingDestination {
    private final Store streamStore;
    private final Target streamTarget;
    private final String nodeName;
    private final AtomicLong recordCount = new AtomicLong();
    private final SegmentOutputStream segmentOutputStream;
    private final boolean segmentOutput;

    public RollingStreamDestination(final StreamKey key,
                                    final Long frequency,
                                    final SimpleCron schedule,
                                    final long rollSize,
                                    final long creationTime,
                                    final Store streamStore,
                                    final Target streamTarget,
                                    final String nodeName) {
        super(key, frequency, schedule, rollSize, creationTime);

        this.streamStore = streamStore;
        this.streamTarget = streamTarget;
        this.nodeName = nodeName;
        this.segmentOutput = key.isSegmentOutput();

        segmentOutputStream = streamTarget.next().get();
        setOutputStream(new ByteCountOutputStream(segmentOutputStream));
    }

    @Override
    protected void onGetOutputStream(final Consumer<Throwable> exceptionConsumer) {
        // Insert a segment marker before we write the next record regardless of whether the header has actually
        // been written. This is because we always make an allowance for the existence of a header in a segmented
        // stream when viewing data.
        insertSegmentMarker(exceptionConsumer);

        recordCount.incrementAndGet();

        super.onGetOutputStream(exceptionConsumer);
    }

    @Override
    protected void beforeRoll(final Consumer<Throwable> exceptionConsumer) {
        // Writing a segment marker here ensures there is always a marker written before the footer regardless or
        // whether a footer is actually written. We do this because we always make an allowance for a footer for data
        // display purposes.
        insertSegmentMarker(exceptionConsumer);

        super.beforeRoll(exceptionConsumer);
    }

    @Override
    protected void afterRoll(final Consumer<Throwable> exceptionConsumer) {
        try {
    //        // Write meta data to stream target.
    //        final AttributeMap attributeMap = new AttributeMap();
    //        attributeMap.put(StreamDataSource.REC_WRITE, recordCount.toString());

            // TODO : @66 DO WE REALLY NEED TO KNOW WHAT NODE PROCESSED A STREAM AS THE DATA IS AVAILABLE ON STREAM TASK???
    //        attributeMap.put(StreamAttributeConstants.NODE, nodeName);
            streamTarget.getAttributes().put(MetaFieldNames.REC_WRITE, recordCount.toString());
            streamTarget.close();
        } catch (final IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private void insertSegmentMarker(final Consumer<Throwable> exceptionConsumer) {
        try {
            // Add a segment marker to the output stream if we are segmenting.
            if (segmentOutput) {
                segmentOutputStream.addSegment();
            }
        } catch (final IOException e) {
            exceptionConsumer.accept(e);
        }
    }
}
