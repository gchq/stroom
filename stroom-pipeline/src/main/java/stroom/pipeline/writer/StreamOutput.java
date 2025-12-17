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

package stroom.pipeline.writer;

import stroom.data.store.api.WrappedSegmentOutputStream;

import java.io.IOException;
import java.io.OutputStream;

public class StreamOutput implements Output {

    private final WrappedSegmentOutputStream wrappedSegmentOutputStream;
    private final boolean segmentOutput;

    public StreamOutput(final WrappedSegmentOutputStream wrappedSegmentOutputStream,
                        final boolean segmentOutput) {
        this.wrappedSegmentOutputStream = wrappedSegmentOutputStream;
        this.segmentOutput = segmentOutput;
    }

    @Override
    public OutputStream getOutputStream() {
        return wrappedSegmentOutputStream;
    }

    /**
     * Insert segment markers after the header and after every record.
     */
    @Override
    public void insertSegmentMarker() throws IOException {
        // Add a segment marker to the output stream if we are segmenting.
        if (segmentOutput) {
            if (wrappedSegmentOutputStream != null) {
                //This can happen if stream type isn't set due to incorrect / incomplete configuration
                wrappedSegmentOutputStream.addSegment();
            }
        }
    }

//    @Override
//    public void startZipEntry() throws IOException {
//        Output.super.startZipEntry();
//    }
//
//    @Override
//    public void endZipEntry() throws IOException {
//        Output.super.endZipEntry();
//    }
//
//    @Override
//    public boolean isZip() {
//        return Output.super.isZip();
//    }

    @Override
    public long getCurrentOutputSize() {
        if (wrappedSegmentOutputStream != null) {
            return wrappedSegmentOutputStream.getPosition();
        }
        return 0;
    }

    @Override
    public boolean getHasBytesWritten() {
        return getCurrentOutputSize() > 0;
    }

    @Override
    public void write(final byte[] bytes) throws IOException {
        wrappedSegmentOutputStream.write(bytes);
    }

    @Override
    public void close() throws IOException {
        wrappedSegmentOutputStream.close();
    }
}
