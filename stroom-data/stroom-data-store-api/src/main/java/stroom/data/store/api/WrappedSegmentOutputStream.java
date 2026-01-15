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

package stroom.data.store.api;

import java.io.IOException;

public class WrappedSegmentOutputStream extends SegmentOutputStream {

    private final SegmentOutputStream segmentOutputStream;

    public WrappedSegmentOutputStream(final SegmentOutputStream segmentOutputStream) {
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

    @Override
    public void close() throws IOException {
        segmentOutputStream.close();
    }

    @Override
    public void flush() throws IOException {
        segmentOutputStream.flush();
    }

    @Override
    public void write(final byte[] b, final int off, final int len) throws IOException {
        segmentOutputStream.write(b, off, len);
    }

    @Override
    public void write(final byte[] b) throws IOException {
        segmentOutputStream.write(b);
    }

    @Override
    public void write(final int b) throws IOException {
        segmentOutputStream.write(b);
    }
}
