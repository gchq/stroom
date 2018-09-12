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

package stroom.pipeline.destination;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.util.io.ByteCountOutputStream;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

public abstract class RollingDestination implements Destination {
    private static final Logger LOGGER = LoggerFactory.getLogger(RollingDestination.class);

    private static final int ONE_MINUTE = 60000;

    private final Object key;
    private final long frequency;
    private final long rollSize;
    private final long creationTime;
    private volatile byte[] footer;

    private volatile long lastFlushTime;

    private final ReentrantLock lock = new ReentrantLock();

    private volatile boolean rolled;

    private ByteCountOutputStream outputStream;

    protected RollingDestination(final Object key,
                                 final long frequency,
                                 final long rollSize,
                                 final long creationTime) {
        this.key = key;
        this.frequency = frequency;
        this.rollSize = rollSize;
        this.creationTime = creationTime;
    }

    void lock() {
        lock.lock();
    }

    boolean tryLock() {
        return lock.tryLock();
    }

    void unlock() {
        lock.unlock();
    }

    protected final Object getKey() {
        return key;
    }

    protected void setOutputStream(final ByteCountOutputStream outputStream) {
        this.outputStream = outputStream;
    }

    @Override
    public final OutputStream getByteArrayOutputStream() throws IOException {
        return getOutputStream(null, null);
    }

    @Override
    public final OutputStream getOutputStream(final byte[] header, final byte[] footer) throws IOException {
        if (rolled) {
            return null;
        }

        final Collection<Throwable> exceptions = new ArrayList<>();

        // this.header = header;
        this.footer = footer;

        // If we haven't written yet then create the output stream and
        // write a header if we have one.
        if (header != null && header.length > 0 && outputStream != null && outputStream.getCount() == 0) {
            // Write the header.
            write(header);
        }

        onGetOutputStream(exceptions::add);

        throwFirstAsIOExceptions(exceptions);

        return outputStream;
    }

    /**
     * Try to flush this destination if it needs to and roll it if it needs to.
     * If this destination is rolled then this method will return true.
     *
     * @return True if this destination has been rolled.
     */
    boolean tryFlushAndRoll(final boolean force, final long currentTime) throws IOException {
        final Collection<Throwable> exceptions = new ArrayList<>();

        try {
            if (!rolled) {
                // Flush the output if we need to.
                if (force || shouldFlush(currentTime)) {
                    try {
                        flush();
                    } catch (final Throwable t) {
                        exceptions.add(t);
                    }
                }

                // Roll the output if we need to.
                if (force || shouldRoll(currentTime)) {
                    try {
                        roll();
                    } catch (final Throwable t) {
                        exceptions.add(t);
                    }
                }
            }
        } catch (final Throwable t) {
            exceptions.add(t);
        }

        throwFirstAsIOExceptions(exceptions);

        return rolled;
    }

    private boolean shouldFlush(final long currentTime) {
        final long lastFlushTime = this.lastFlushTime;
        this.lastFlushTime = currentTime;
        return lastFlushTime > 0 && currentTime - lastFlushTime > ONE_MINUTE;
    }

    private boolean shouldRoll(final long currentTime) {
        final long oldestAllowed = currentTime - frequency;
        return creationTime < oldestAllowed || outputStream.getCount() > rollSize;
    }

    protected final void roll() throws IOException {
        this.rolled = true;

        final Collection<Throwable> exceptions = new ArrayList<>();

        beforeRoll(exceptions::add);

        // If we have written any data then write a footer if we have one.
        if (footer != null && footer.length > 0 && outputStream != null && outputStream.getCount() > 0) {
            // Write the footer.
            try {
                write(footer);
            } catch (final Throwable e) {
                exceptions.add(e);
            }
        }

        // Try and close the output stream.
        try {
            close();
        } catch (final Throwable e) {
            exceptions.add(e);
        }

        afterRoll(exceptions::add);

        throwFirstAsIOExceptions(exceptions);
    }

    private void write(final byte[] bytes) throws IOException {
        outputStream.write(bytes, 0, bytes.length);
    }

    private void flush() throws IOException {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Flushing: {}", getKey());
        }
        outputStream.flush();
    }

    protected void close() throws IOException {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Closing: {}", getKey());
        }
        outputStream.close();
    }

    @Override
    public String toString() {
        return (key != null) ? key.toString() : null;
    }

    /**
     * Given a collection of exceptions accumulated during a process, converts/casts them all to IOExceptions,
     * logging them all, it then throws the first one, if any have been seen.
     * If no exceptions were seen, this function will simply return.
     *
     * @param exceptions The list of throwables accumulated during a process.
     * @throws IOException If there are any throwables, the first one is thrown as an IOException.
     */
    private void throwFirstAsIOExceptions(final Collection<Throwable> exceptions) throws IOException {
        final Optional<IOException> firstException = exceptions.stream()
                .map(e -> {
                    LOGGER.error(e.getMessage(), e);

                    if (e instanceof IOException) {
                        return (IOException) e;
                    }

                    return new IOException(e.getMessage(), e);
                })
                .findFirst();
        if (firstException.isPresent()) {
            throw firstException.get();
        }
    }

    /**
     * Child classes can take action when the output stream is requested.
     *
     * @param exceptionConsumer Exceptions are gathered up and thrown as part of a larger process.
     */
    void onGetOutputStream(Consumer<Throwable> exceptionConsumer) {
    }

    /**
     * Child classes can take action before the footer has been written to the particular instance of the destination.
     *
     * @param exceptionConsumer Exceptions are gathered up and thrown as part of a larger process.
     */
    void beforeRoll(Consumer<Throwable> exceptionConsumer) {
    }

    /**
     * Child classes can take action after the footer has been written to the particular instance of the destination.
     *
     * @param exceptionConsumer Exceptions are gathered up and thrown as part of a larger process.
     */
    void afterRoll(Consumer<Throwable> exceptionConsumer) {
    }
}
