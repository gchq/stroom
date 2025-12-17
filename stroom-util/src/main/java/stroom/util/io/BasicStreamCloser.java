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

package stroom.util.io;

import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

public class BasicStreamCloser implements StreamCloser {

    private static final Logger LOGGER = LoggerFactory.getLogger(BasicStreamCloser.class);
    private final List<Closeable> list = new ArrayList<>();

    @Inject
    public BasicStreamCloser() {
    }

//    public BasicStreamCloser(final Closeable... closeables) {
//        add(closeables);
//    }
//
//    public BasicStreamCloser(final Closeable closeable) {
//        add(closeable);
//    }
//
//    public BasicStreamCloser add(final Closeable... closeables) {
//        for (final Closeable closeable : closeables) {
//            add(closeable);
//        }
//
//        return this;
//    }

    public BasicStreamCloser add(final Closeable closeable) {
        // Add items to the beginning of the list so that they are closed in the
        // opposite order to the order they were opened in.
        list.add(0, closeable);
        return this;
    }

    @Override
    public void close() throws IOException {
        IOException ioException = null;

        for (final Closeable closeable : list) {
            try {
                if (closeable != null) {
                    if (closeable instanceof OutputStream) {
                        // Make sure output streams get flushed.
                        try {
                            ((OutputStream) closeable).flush();
                        } catch (final IOException e) {
                            LOGGER.error("Unable to flush stream!", e);

                            if (ioException == null) {
                                ioException = e;
                            }
                        } catch (final RuntimeException e) {
                            LOGGER.error("Unable to flush stream!", e);

                            if (ioException == null) {
                                ioException = new IOException(e);
                            }
                        }
                    } else if (closeable instanceof Writer) {
                        // Make sure writers get flushed.
                        try {
                            ((Writer) closeable).flush();
                        } catch (final RuntimeException e) {
                            LOGGER.error("Unable to flush stream!", e);

                            if (ioException == null) {
                                ioException = new IOException(e);
                            }
                        }
                    }

                    // Close the stream.
                    closeable.close();
                }
            } catch (final IOException e) {
                LOGGER.error("Unable to close stream!", e);

                if (ioException == null) {
                    ioException = e;
                }
            } catch (final RuntimeException e) {
                LOGGER.error("Unable to close stream!", e);

                if (ioException == null) {
                    ioException = new IOException(e);
                }
            }
        }

        // Remove all items from the list as they are now closed.
        list.clear();

        if (ioException != null) {
            throw ioException;
        }
    }
}
