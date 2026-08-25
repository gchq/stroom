/*
 * Copyright 2026 Crown Copyright
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

package stroom.proxy.app.pipeline.stress;

import stroom.proxy.app.pipeline.queue.FileGroupQueue;
import stroom.proxy.app.pipeline.queue.FileGroupQueueItem;
import stroom.proxy.app.pipeline.queue.FileGroupQueueMessage;
import stroom.proxy.app.pipeline.queue.QueueType;

import com.codahale.metrics.health.HealthCheck;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A {@link FileGroupQueue} that wraps a real queue and injects faults around it.
 * <p>
 * The decorator never fabricates queue behaviour of its own - every call either
 * throws before reaching the delegate, or is passed straight through, or reaches
 * the delegate and then throws. Anything else would test the decorator rather
 * than the queue.
 * </p>
 */
public class FaultInjectingFileGroupQueue implements FileGroupQueue {

    private final FileGroupQueue delegate;
    private final FaultPolicy faultPolicy;

    public FaultInjectingFileGroupQueue(final FileGroupQueue delegate,
                                        final FaultPolicy faultPolicy) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.faultPolicy = Objects.requireNonNull(faultPolicy, "faultPolicy");
    }

    public FileGroupQueue getDelegate() {
        return delegate;
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    public QueueType getType() {
        return delegate.getType();
    }

    @Override
    public void publish(final FileGroupQueueMessage message) throws IOException {
        faultPolicy.maybeFail(FaultPoint.QUEUE_PUBLISH);
        faultPolicy.maybeDelay();

        delegate.publish(message);

        // The message is now durably on the queue but the caller is about to be
        // told it is not. The producer will republish, so the consumer sees the
        // same file group twice.
        faultPolicy.maybeFail(FaultPoint.QUEUE_PUBLISH_AFTER);
    }

    @Override
    public Optional<FileGroupQueueItem> next() throws IOException {
        faultPolicy.maybeFail(FaultPoint.QUEUE_NEXT);
        faultPolicy.maybeDelay();

        return delegate.next().map(item -> new FaultInjectingItem(item, faultPolicy));
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }

    @Override
    public HealthCheck.Result healthCheck() {
        return delegate.healthCheck();
    }

    // -------------------------------------------------------------------------

    private static final class FaultInjectingItem implements FileGroupQueueItem {

        private final FileGroupQueueItem delegate;
        private final FaultPolicy faultPolicy;

        private FaultInjectingItem(final FileGroupQueueItem delegate,
                                   final FaultPolicy faultPolicy) {
            this.delegate = delegate;
            this.faultPolicy = faultPolicy;
        }

        @Override
        public String getId() {
            return delegate.getId();
        }

        @Override
        public FileGroupQueueMessage getMessage() {
            return delegate.getMessage();
        }

        @Override
        public Map<String, String> getMetadata() {
            return delegate.getMetadata();
        }

        @Override
        public void acknowledge() throws IOException {
            // Failing here leaves the item in-flight: the work is done but the
            // queue does not know it. Reopening the queue must recover it.
            faultPolicy.maybeFail(FaultPoint.QUEUE_ACK);

            delegate.acknowledge();

            faultPolicy.maybeFail(FaultPoint.QUEUE_ACK_AFTER);
        }

        @Override
        public void fail(final Throwable error) throws IOException {
            faultPolicy.maybeFail(FaultPoint.QUEUE_FAIL);
            delegate.fail(error);
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }
    }
}
