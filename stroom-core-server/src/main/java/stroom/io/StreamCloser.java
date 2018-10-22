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
import javax.persistence.OptimisticLockException;
import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

@Component
@Scope(StroomScope.TASK)
public class StreamCloser implements Closeable {
    private static final Logger LOGGER = LoggerFactory.getLogger(StreamCloser.class);
    private final List<Closeable> list = new ArrayList<>();

    @Resource
    private StreamStore streamStore;

    public StreamCloser() {
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
                            // Close the stream target.
                            try {
                                streamStore.closeStreamTarget(streamTarget);
                            } catch (final OptimisticLockException e) {
                                // This exception will be thrown is the stream target has already been deleted by another thread if it was superseded.
                                LOGGER.debug("Optimistic lock exception thrown when closing stream target (see trace for details)");
                                LOGGER.trace(e.getMessage(), e);
                            } catch (final RuntimeException e) {
                                LOGGER.error(e.getMessage(), e);
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
