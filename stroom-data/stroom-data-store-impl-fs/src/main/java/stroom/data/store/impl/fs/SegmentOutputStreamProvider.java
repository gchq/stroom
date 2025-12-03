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

package stroom.data.store.impl.fs;

import stroom.data.store.api.SegmentOutputStream;
import stroom.data.store.api.WrappedSegmentOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;

public class SegmentOutputStreamProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(SegmentOutputStreamProvider.class);

    private long index = -1;
    private final String dataTypeName;
    private final SegmentOutputStream boundaryOutputStream;
    private final SegmentOutputStream segmentOutputStream;

    public SegmentOutputStreamProvider(final InternalTarget target, final String dataTypeName) {
        this.dataTypeName = dataTypeName;

        boundaryOutputStream = new RASegmentOutputStream(target.getOutputStream(),
                () -> target.getChildOutputStream(InternalStreamTypeNames.BOUNDARY_INDEX));
        segmentOutputStream = new RASegmentOutputStream(boundaryOutputStream,
                () -> target.getChildOutputStream(InternalStreamTypeNames.SEGMENT_INDEX));
    }

    public SegmentOutputStream get(final long index) {
        try {
            if (this.index >= index) {
                throw new IOException("Output stream already provided for index " + index);
            }

            // Move up to the right index if this OS is behind, i.e. it hasn't been requested for a certain
            // data type before.
            while (this.index < index - 1) {
                LOGGER.debug("Fast forwarding for " + dataTypeName);
                this.index++;

                // Start writing segments on the 2nd stream to be added
                if (index > 0) {
                    boundaryOutputStream.flush();
                    boundaryOutputStream.addSegment();
                }
            }

            this.index++;

            // Start writing segments on the 2nd stream to be added
            if (index > 0) {
                boundaryOutputStream.flush();
                boundaryOutputStream.addSegment();
            }

            return new WrappedSegmentOutputStream(segmentOutputStream) {
                @Override
                public void close() {
                    // Do nothing.
                }
            };
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
