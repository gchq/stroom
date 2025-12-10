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

/**
 * <p>
 * This class extends <code>OutputStream</code> to record segments within an
 * output stream that can later be retrieved individually or filtered on reading
 * by a <code>SegmentInputStream</code>.
 * </p>
 */
public abstract class SegmentOutputStream extends OutputStream {

    /**
     * Adds a segment boundary to the output stream. All bytes written between
     * the start of the output or the last boundary will be considered a segment
     * by the <code>SegmentInputStream</code>.
     */
    public abstract void addSegment() throws IOException;

    /**
     * Adds a segment boundary to the output stream at a given byte position.
     * All bytes written between the start of the output or the last boundary
     * will be considered a segment by the <code>SegmentInputStream</code>.
     *
     * @param position The byte position of the end of the segment.
     */
    public abstract void addSegment(final long position) throws IOException;

    /**
     * Gets the current byte position in the output stream.
     */
    public abstract long getPosition();
}
