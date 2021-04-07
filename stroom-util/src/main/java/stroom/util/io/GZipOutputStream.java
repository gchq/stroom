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

package stroom.util.io;

import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Extends `GZIPOutputStream` to provide a way of returning the number of bytes written (after compression)
 * and to customise the compression level
 */
public class GZipOutputStream extends GZIPOutputStream {
    public GZipOutputStream(final OutputStream out) throws IOException {
        super(out, GZipConstants.DEFAULT_BUFFER_SIZE);
    }

    public GZipOutputStream(final OutputStream out, final int size) throws IOException {
        super(out, size);
    }

    /**
     * Get the number of bytes written (after compression)
     */
    public long getBytesWritten() {
        return def.getBytesWritten();
    }

    /**
     * Set the GZIP compression level
     * @param compressionLevel Valid range is `0` through `9`, or `-1` to use the default compression level
     */
    public void setCompressionLevel(final int compressionLevel) {
        def.setLevel(compressionLevel);
    }
}
