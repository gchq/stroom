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

package stroom.pipeline.server.writer;

import stroom.streamstore.server.fs.serializable.RASegmentOutputStream;
import stroom.streamstore.server.fs.serializable.SegmentOutputStream;
import stroom.util.io.WrappedOutputStream;

import java.io.IOException;

public class WrappedSegmentOutputStream extends WrappedOutputStream implements SegmentOutputStream {
    private final RASegmentOutputStream segmentOutputStream;

    public WrappedSegmentOutputStream(final RASegmentOutputStream segmentOutputStream) {
        super(segmentOutputStream);
        this.segmentOutputStream = segmentOutputStream;
    }

    @Override
    public void addSegment() throws IOException {
        segmentOutputStream.addSegment();
    }

    @Override
    public void addSegment(final long position) throws IOException {
        segmentOutputStream.addSegment(position);
    }

    @Override
    public long getPosition() {
        return segmentOutputStream.getPosition();
    }
}
