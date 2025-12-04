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

package stroom.pipeline.destination;

import stroom.pipeline.writer.Output;
import stroom.util.scheduler.Trigger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

public abstract class RollingDestination implements Destination {

    private static final Logger LOGGER = LoggerFactory.getLogger(RollingDestination.class);

    private static final int ONE_MINUTE = 60000;

    private final Object key;
    private final Instant oldestAllowed;
    private final long rollSize;
    private volatile byte[] footer;

    private volatile Instant lastFlushTime;

    private final ReentrantLock lock = new ReentrantLock();

    private volatile boolean rolled;

    private Output output;

    protected RollingDestination(final Object key,
                                 final Trigger frequencyTrigger,
                                 final Trigger cronTrigger,
                                 final long rollSize,
                                 final Instant creationTime) {
        this.key = key;
        this.rollSize = rollSize;

        // Determine the oldest this destination can be.
        Instant time = null;
        if (cronTrigger != null) {
            time = cronTrigger.getNextExecutionTimeAfter(creationTime);
        }
        if (frequencyTrigger != null) {
            final Instant value = frequencyTrigger.getNextExecutionTimeAfter(creationTime);
            if (time == null || time.isAfter(value)) {
                time = value;
            }
        }
        oldestAllowed = time;
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

    protected void setOutput(final Output output) {
        this.output = output;
    }

    @Override
    public final OutputStream getOutputStream() throws IOException {
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
        if (header != null && header.length > 0 && output != null && !output.getHasBytesWritten()) {
            // Write the header.
            write(header);
        }

        onGetOutputStream(exceptions::add);

        throwFirstAsIOExceptions(exceptions);

        return output.getOutputStream();
    }

    /**
     * Try to flush this destination if it needs to and roll it if it needs to.
     * If this destination is rolled then this method will return true.
     *
     * @return True if this destination has been rolled.
     */
    boolean tryFlushAndRoll(final boolean force, final Instant currentTime) throws IOException {
        final Collection<Throwable> exceptions = new ArrayList<>();

        try {
            if (!rolled) {
                // Flush the output if we need to.
                if (force || shouldFlush(currentTime)) {
                    try {
                        flush();
                    } catch (final RuntimeException e) {
                        exceptions.add(e);
                    }
                }

                // Roll the output if we need to.
                if (force || shouldRoll(currentTime)) {
                    try {
                        roll();
                    } catch (final RuntimeException e) {
                        exceptions.add(e);
                    }
                }
            }
        } catch (final RuntimeException e) {
            exceptions.add(e);
        }

        throwFirstAsIOExceptions(exceptions);

        return rolled;
    }

    private boolean shouldFlush(final Instant currentTime) {
        final Instant lastFlushTime = this.lastFlushTime;
        this.lastFlushTime = currentTime;
        return lastFlushTime != null && lastFlushTime.plus(1, ChronoUnit.MINUTES).isBefore(currentTime);
    }

    private boolean shouldRoll(final Instant currentTime) {
        return (oldestAllowed != null && currentTime.isAfter(oldestAllowed)) ||
                output.getCurrentOutputSize() > rollSize;
    }

    protected final void roll() throws IOException {
        this.rolled = true;

        final Collection<Throwable> exceptions = new ArrayList<>();

        beforeRoll(exceptions::add);

        // If we have written any data then write a footer if we have one.
        if (footer != null && footer.length > 0 && output != null && output.getCurrentOutputSize() > 0) {
            // Write the footer.
            try {
                write(footer);
            } catch (final RuntimeException e) {
                exceptions.add(e);
            }
        }

        // Try and close the output stream.
        try {
            close();
        } catch (final RuntimeException e) {
            exceptions.add(e);
        }

        afterRoll(exceptions::add);

        throwFirstAsIOExceptions(exceptions);
    }

    private void write(final byte[] bytes) throws IOException {
        output.write(bytes);
    }

    private void flush() throws IOException {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Flushing: {}", getKey());
        }
        output.getOutputStream().flush();
    }

    protected void close() throws IOException {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Closing: {}", getKey());
        }
        output.close();
    }

    @Override
    public String toString() {
        return (key != null)
                ? key.toString()
                : null;
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
    protected void onGetOutputStream(final Consumer<Throwable> exceptionConsumer) {
    }

    /**
     * Child classes can take action before the footer has been written to the particular instance of the destination.
     *
     * @param exceptionConsumer Exceptions are gathered up and thrown as part of a larger process.
     */
    protected void beforeRoll(final Consumer<Throwable> exceptionConsumer) {
    }

    /**
     * Child classes can take action after the footer has been written to the particular instance of the destination.
     *
     * @param exceptionConsumer Exceptions are gathered up and thrown as part of a larger process.
     */
    protected void afterRoll(final Consumer<Throwable> exceptionConsumer) {
    }
}
