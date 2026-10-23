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

package stroom.planb.impl.fs;

import stroom.planb.impl.PlanBConstants;
import stroom.planb.impl.dao.trace.QueueItem;
import stroom.planb.shared.SharedFileStoreSettings;
import stroom.util.io.PathSegmentUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * One shard's queue folder on the shared filesystem: where it is, and how deep.
 *
 * <p>Only the producer's side. What is waiting there is found by the merge, the same way it finds
 * every other kind of work for a shard, and leaves the same way: an item carries the marker that says
 * it is finished, and is deleted once the store that consumed it is published.
 */
public final class ShardQueue {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ShardQueue.class);

    private final Path shardDir;

    private ShardQueue(final Path shardDir) {
        this.shardDir = shardDir;
    }

    public static ShardQueue of(final SharedFileStoreSettings settings,
                         final String pathwaysDocUuid,
                         final int shard) {
        return new ShardQueue(Path.of(settings.getSharedPath())
                .resolve(PlanBConstants.QUEUE_DIR_NAME)
                .resolve(PathSegmentUtil.requireSafeSegment(pathwaysDocUuid))
                .resolve(PlanBConstants.formatShardIndex(shard)));
    }

    /**
     * How many shards a document's queue and model are split across. Clamped, because a document
     * saved with nothing set would otherwise route every trace to shard -1.
     */
    public static int shardCount(final SharedFileStoreSettings settings) {
        return Math.max(1, settings.getShardCount());
    }

    /**
     * Where the folder is. Not created here: the writer creates the item's own temporary directory
     * beneath it, which brings this into being with it.
     */
    public Path dir() {
        return shardDir;
    }

    /**
     * Whether more than {@code max} items are waiting. Counts no further than it has to, because the
     * answer is wanted on every hand-over and the folder is on a shared mount.
     *
     * @param max how deep the caller is prepared to let the folder get. What to do about it is the
     *            caller's: this only measures.
     */
    public boolean isDeeperThan(final int max) throws IOException {
        if (!Files.isDirectory(shardDir)) {
            return false;
        }
        try (final Stream<Path> stream = Files.list(shardDir)) {
            return stream.filter(QueueItem::isItem).limit(max + 1L).count() > max;
        }
    }

    @Override
    public String toString() {
        return shardDir.toString();
    }
}
