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

package stroom.streamstore.store.impl.fs.serializable;

import stroom.util.io.StreamUtil;
import stroom.util.xml.SAXParserFactoryFactory;

import javax.xml.parsers.SAXParserFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/**
 * This class takes an input stream for raw data and ensures it is written to
 * the stream store with segment boundaries added. This allows all raw data to
 * be paged.
 * <p>
 * XML and text must be treated differently as we want segment boundaries to
 * appear between first level elements in XML and after every new line in text.
 * In order to achieve this, this class attempts to determine if the input is
 * XML or text by looking for "<?xml" in the first 2000 bytes of the input. If
 * found the input stream is treated as XML.
 */
public class RawInputSegmentWriter {
    // private static final int BYTES_PER_SEGMENT = 10000;
    // private static final int TYPE_BUFFER_SIZE = 2000;
    private static final SAXParserFactory PARSER_FACTORY;

    static {
        PARSER_FACTORY = SAXParserFactoryFactory.newInstance();
        PARSER_FACTORY.setNamespaceAware(true);
    }

    /**
     * Writes an input stream to a segment output stream and inserts segment
     * boundaries at appropriate positions depending on the input type. The
     * input type (XML and text) is determined within this method.
     *
     * @param inputStream         The input stream to read.
     * @param segmentOutputStream The segment output stream to write to.
     */
    public void write(final InputStream inputStream, final RASegmentOutputStream segmentOutputStream) {
        write(inputStream, segmentOutputStream, true);
    }

    /**
     * Writes an input stream to a segment output stream and inserts segment
     * boundaries at appropriate positions depending on the input type. The
     * input type (XML and text) is determined within this method.
     *
     * @param inputStream         The input stream to read.
     * @param segmentOutputStream The segment output stream to write to.
     * @param close               both streams at end?
     */
    public long write(final InputStream inputStream, final RASegmentOutputStream segmentOutputStream,
                      final boolean close) {
        long bytesWritten = 0;
        try {
            try {
                bytesWritten = StreamUtil.streamToStream(inputStream, segmentOutputStream, close);
            } finally {
                try {
                    // Ensure all streams are closed.
                    if (segmentOutputStream != null) {
                        segmentOutputStream.flush();
                        if (close) {
                            segmentOutputStream.close();
                        }
                    }

                } finally {
                    if (close && inputStream != null) {
                        inputStream.close();
                    }
                }
            }
            return bytesWritten;
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
