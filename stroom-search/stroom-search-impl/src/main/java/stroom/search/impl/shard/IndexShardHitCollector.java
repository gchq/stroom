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

package stroom.search.impl.shard;

import stroom.pipeline.errorhandler.TerminatedException;
import stroom.task.api.TaskContext;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.SimpleCollector;

import java.io.IOException;
import java.util.OptionalInt;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

class IndexShardHitCollector extends SimpleCollector {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(IndexShardHitCollector.class);

    private final TaskContext taskContext;
    //an empty optional is used as a marker to indicate no more items will be added
    private final LinkedBlockingQueue<OptionalInt> docIdStore;
    private final AtomicLong hitCount;
    private int docBase;

    IndexShardHitCollector(final TaskContext taskContext,
                           final LinkedBlockingQueue<OptionalInt> docIdStore,
                           final AtomicLong hitCount) {
        this.docIdStore = docIdStore;
        this.taskContext = taskContext;
        this.hitCount = hitCount;

        info(() -> "Searching...");
    }

    @Override
    protected void doSetNextReader(final LeafReaderContext context) throws IOException {
        super.doSetNextReader(context);
        docBase = context.docBase;
    }

    @Override
    public void collect(final int doc) {
        // Pause the current search if the deque is full.
        final int docId = docBase + doc;

        try {
            docIdStore.put(OptionalInt.of(docId));
            info(() -> "Found " + hitCount + " hits");
        } catch (final InterruptedException e) {
            // Continue to interrupt.
            info(() -> "Quitting...");
            Thread.currentThread().interrupt();
            LOGGER.debug(e::getMessage, e);
            throw new TerminatedException();
        } catch (final RuntimeException e) {
            LOGGER.error(e::getMessage, e);
        }

        // Add to the hit count.
        hitCount.getAndIncrement();
    }

    private void info(final Supplier<String> message) {
        taskContext.info(message);
        LOGGER.debug(message);
    }

    @Override
    public boolean needsScores() {
        return false;
    }
}

