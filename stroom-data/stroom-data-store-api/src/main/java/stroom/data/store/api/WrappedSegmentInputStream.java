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
import java.io.OutputStream;

public class WrappedSegmentInputStream extends SegmentInputStream {

    private final SegmentInputStream segmentInputStream;

    public WrappedSegmentInputStream(final SegmentInputStream segmentInputStream) {
        this.segmentInputStream = segmentInputStream;
    }

    @Override
    public long count() {
        return segmentInputStream.count();
    }

    @Override
    public void include(final long segment) {
        segmentInputStream.include(segment);
    }

    @Override
    public void includeAll() {
        segmentInputStream.includeAll();
    }

    @Override
    public void exclude(final long segment) {
        segmentInputStream.exclude(segment);
    }

    @Override
    public void excludeAll() {
        segmentInputStream.excludeAll();
    }

    @Override
    public long size() {
        return segmentInputStream.size();
    }

    @Override
    public int read() throws IOException {
        return segmentInputStream.read();
    }

    @Override
    public int read(final byte[] b) throws IOException {
        return segmentInputStream.read(b);
    }

    @Override
    public int read(final byte[] b, final int off, final int len) throws IOException {
        return segmentInputStream.read(b, off, len);
    }

    @Override
    public byte[] readAllBytes() throws IOException {
        return segmentInputStream.readAllBytes();
    }

    @Override
    public int readNBytes(final byte[] b, final int off, final int len) throws IOException {
        return segmentInputStream.readNBytes(b, off, len);
    }

    @Override
    public long skip(final long n) throws IOException {
        return segmentInputStream.skip(n);
    }

    @Override
    public int available() throws IOException {
        return segmentInputStream.available();
    }

    @Override
    public void close() throws IOException {
        segmentInputStream.close();
    }

    @Override
    public synchronized void mark(final int readlimit) {
        segmentInputStream.mark(readlimit);
    }

    @Override
    public synchronized void reset() throws IOException {
        segmentInputStream.reset();
    }

    @Override
    public boolean markSupported() {
        return segmentInputStream.markSupported();
    }

    @Override
    public long transferTo(final OutputStream out) throws IOException {
        return segmentInputStream.transferTo(out);
    }
}
