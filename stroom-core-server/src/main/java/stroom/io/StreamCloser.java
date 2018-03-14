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

package stroom.io;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.streamstore.StreamStore;
import stroom.streamstore.StreamTarget;
import stroom.guice.PipelineScoped;

import javax.inject.Inject;
import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

@PipelineScoped
public class StreamCloser implements Closeable {
    private static final Logger LOGGER = LoggerFactory.getLogger(StreamCloser.class);
    private final List<Closeable> list = new ArrayList<>();

    private final StreamStore streamStore;

    // For stream target's we can delete them on closing if they are no-longer
    // required
    private boolean delete = false;

    @Inject
    public StreamCloser(final StreamStore streamStore) {
        this.streamStore = streamStore;
    }

    public StreamCloser() {
        this.streamStore = null;
    }

    public StreamCloser(final Closeable... closeables) {
        this.streamStore = null;
        add(closeables);
    }

    public StreamCloser(final Closeable closeable) {
        this.streamStore = null;
        add(closeable);
    }

    public StreamCloser add(final Closeable... closeables) {
        for (final Closeable closeable : closeables) {
            add(closeable);
        }

        return this;
    }

    public void setDelete(boolean delete) {
        this.delete = delete;
    }

    public StreamCloser add(final Closeable closeable) {
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
                        } catch (final Throwable e) {
                            LOGGER.error("Unable to flush stream!", e);

                            if (ioException == null) {
                                ioException = new IOException(e);
                            }
                        }
                    } else if (closeable instanceof Writer) {
                        // Make sure writers get flushed.
                        try {
                            ((Writer) closeable).flush();
                        } catch (final Throwable e) {
                            LOGGER.error("Unable to flush stream!", e);

                            if (ioException == null) {
                                ioException = new IOException(e);
                            }
                        }
                    }

                    if (closeable instanceof StreamTarget) {
                        final StreamTarget streamTarget = (StreamTarget) closeable;

                        // Only call the API on the root parent stream
                        if (streamTarget.getParent() == null) {
                            if (delete) {
                                streamStore.deleteStreamTarget(streamTarget);
                            } else {
                                streamStore.closeStreamTarget(streamTarget);
                            }
                        }
                    } else {
                        // Close the stream.
                        closeable.close();

                    }
                }
            } catch (final IOException e) {
                LOGGER.error("Unable to close stream!", e);

                if (ioException == null) {
                    ioException = e;
                }
            } catch (final Throwable e) {
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
