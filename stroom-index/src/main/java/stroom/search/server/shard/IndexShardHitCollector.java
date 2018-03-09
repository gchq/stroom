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

package stroom.search.server.shard;

import org.apache.lucene.index.AtomicReaderContext;
import org.apache.lucene.search.Collector;
import org.apache.lucene.search.Scorer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.pipeline.server.errorhandler.TerminatedException;
import stroom.task.server.TaskContext;
import stroom.util.shared.ModelStringUtil;

import java.io.IOException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class IndexShardHitCollector extends Collector {
    private static final Logger LOGGER = LoggerFactory.getLogger(IndexShardHitCollector.class);

    private final TaskContext taskContext;
    private final LinkedBlockingQueue<Integer> docIdStore;
    private final AtomicLong hitCount;
    private int docBase;
    private Long pauseTime;

    public IndexShardHitCollector(final TaskContext taskContext, final LinkedBlockingQueue<Integer> docIdStore,
                                  final AtomicLong hitCount) {
        this.docIdStore = docIdStore;
        this.taskContext = taskContext;
        this.hitCount = hitCount;
    }

    @Override
    public void collect(final int doc) {
        // Pause the current search if the deque is full.
        final int docId = docBase + doc;

        try {

            while (!docIdStore.offer(docId, 1, TimeUnit.SECONDS) && !taskContext.isTerminated()) {
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
    public boolean acceptsDocsOutOfOrder() {
        return true;
    }

    @Override
    public void setNextReader(final AtomicReaderContext context) throws IOException {
        this.docBase = context.docBase;
    }

    @Override
    public void setScorer(final Scorer scorer) throws IOException {
        // Not implemented as we don't score docs.
    }
}
