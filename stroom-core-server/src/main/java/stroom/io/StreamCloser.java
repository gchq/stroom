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
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import stroom.streamstore.server.StreamStore;
import stroom.streamstore.server.StreamTarget;
import stroom.util.spring.StroomScope;

import javax.annotation.Resource;
import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Scope(StroomScope.TASK)
public class StreamCloser implements Closeable {
    private static final Logger LOGGER = LoggerFactory.getLogger(StreamCloser.class);
    private final List<Closeable> list = new ArrayList<>();
    private final Map<Closeable, IOException> map = new HashMap<>();

    @Resource
    private StreamStore streamStore;

    // For stream target's we can delete them on closing if they are no-longer
    // required
    private boolean delete = false;

    public StreamCloser() {
    }

    public StreamCloser(final Closeable... closeables) {
        add(closeables);
    }

    public StreamCloser(final Closeable closeable) {
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
        final IOException ioException = new IOException();

        // Add items to the beginning of the list so that they are closed in the
        // opposite order to the order they were opened in.
        list.add(0, closeable);
        map.put(closeable, ioException);
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

        // Remove all items from the list and map as they are now closed.
        list.clear();
        map.clear();

        if (ioException != null) {
            throw ioException;
        }
    }

    @Override
    protected void finalize() throws Throwable {
        // Error on all unclosed streams.
        for (final Closeable closeable : list) {
            final IOException e = map.get(closeable);
            LOGGER.error("Failed to close " + closeable.getClass().getSimpleName() + " stream opened here", e);
        }
        super.finalize();
    }
}
