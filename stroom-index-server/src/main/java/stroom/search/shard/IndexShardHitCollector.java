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

package stroom.search.shard;

import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.SimpleCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.pipeline.errorhandler.TerminatedException;
import stroom.task.TaskContext;
import stroom.util.shared.ModelStringUtil;

import java.io.IOException;
import java.util.OptionalInt;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class IndexShardHitCollector extends SimpleCollector {
    private static final Logger LOGGER = LoggerFactory.getLogger(IndexShardHitCollector.class);

    private final TaskContext taskContext;
    //an empty optional is used as a marker to indicate no more items will be added
    private final LinkedBlockingQueue<OptionalInt> docIdStore;
    private final AtomicLong hitCount;
    private int docBase;
    private Long pauseTime;

    public IndexShardHitCollector(final TaskContext taskContext,
                                  final LinkedBlockingQueue<OptionalInt> docIdStore,
                                  final AtomicLong hitCount) {
        this.docIdStore = docIdStore;
        this.taskContext = taskContext;
        this.hitCount = hitCount;
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
            while (!docIdStore.offer(OptionalInt.of(docId), 5, TimeUnit.SECONDS) && !taskContext.isTerminated()) {
                if (isProvidingInfo()) {
                    if (pauseTime == null) {
                        pauseTime = System.currentTimeMillis();
                    }

                    final long elapsed = System.currentTimeMillis() - pauseTime;
                    provideInfo("Paused for " + ModelStringUtil.formatDurationString(elapsed));
                    LOGGER.trace("elapsed [{}]", elapsed);
                }
            }
        } catch (final Throwable e) {
            LOGGER.error(e.getMessage(), e);
        }

        // Add to the hit count.
        hitCount.incrementAndGet();

        // Quit searching if the task monitor is set to stop.
        if (taskContext.isTerminated()) {
            if (isProvidingInfo()) {
                if (pauseTime != null) {
                    final long elapsed = System.currentTimeMillis() - pauseTime;
                    provideInfo("Quitting search after pausing for " + ModelStringUtil.formatDurationString(elapsed));
                } else {
                    provideInfo("Quitting...");
                }
            }

            throw new TerminatedException();
        }

        // If we are no longer paused then make sure the client knows we are still searching.
        if (isProvidingInfo()) {
            if (pauseTime != null) {
                pauseTime = null;
                provideInfo("Searching...");
            }
        }
    }

    private boolean isProvidingInfo() {
        return true;
    }

    private void provideInfo(final String message) {
        taskContext.info(message);
        LOGGER.debug(message);
    }

    @Override
    public boolean needsScores() {
        return false;
    }
}
