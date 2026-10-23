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

import stroom.cluster.lock.api.ClusterLockService;
import stroom.security.api.SecurityContext;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;

/**
 * Works a document's shards side by side, one cluster lock each.
 *
 * <p>A shard is a store of its own under a lock of its own, so shards share nothing but the disk and
 * a pass takes as long as its slowest shard rather than the sum of them. Which node works which shard
 * does not matter: the lock decides, and both the work and the state it builds live on the shared
 * filesystem.
 *
 * <p>Says which shards it could not get. {@link ClusterLockService#tryLock} gives nothing back, so a
 * shard held by another node looks exactly like one that was worked — and a pass that runs once a
 * night and quietly leaves a quarter of its shards undone is worth being told about.
 */
public final class ShardPass {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ShardPass.class);

    private final ClusterLockService clusterLockService;
    private final SecurityContext securityContext;
    private final ExecutorService executor;

    /**
     * @param executor the pool to work shards on. Held by whoever makes this rather than made here,
     *                 because how many shards a job works at once is that job's business — the jobs
     *                 that use this compete for one filesystem and are sized for different work.
     */
    public ShardPass(final ClusterLockService clusterLockService,
                     final SecurityContext securityContext,
                     final ExecutorService executor) {
        this.clusterLockService = clusterLockService;
        this.securityContext = securityContext;
        this.executor = executor;
    }

    /**
     * Takes each shard's lock and runs the work under it.
     *
     * <p>Only the shards given are worked, so a caller that can tell a shard has nothing to do leaves
     * it out: a contended {@code tryLock} costs a warning and a query against {@code cluster_lock},
     * and most shards are empty most cycles.
     *
     * @param shards   the shards to work, which the caller has already decided are worth asking for.
     * @param lockName the cluster lock to hold while working a shard.
     * @param work     what to do with a shard, run while its lock is held.
     * @param what     what this pass is doing, for the log where a shard fails.
     * @return the shards whose lock was held by something else, which is not an error — only work put
     * off. Empty where every shard given was worked.
     */
    public Set<Integer> run(final Collection<Integer> shards,
                            final IntFunction<String> lockName,
                            final IntConsumer work,
                            final String what) {
        // Scattered rather than taken in order, so that nodes running this at the same moment are
        // unlikely to reach for the same shard first. In order, every node would queue behind shard 0.
        final List<Integer> scattered = new ArrayList<>(shards);
        Collections.shuffle(scattered);

        final Set<Integer> busy = Collections.synchronizedSet(new LinkedHashSet<>());
        final List<CompletableFuture<Void>> inFlight = new ArrayList<>();
        for (final int shard : scattered) {
            if (Thread.currentThread().isInterrupted()) {
                LOGGER.debug(() -> "Interrupted, starting no more shards for " + what);
                break;
            }
            inFlight.add(start(shard, lockName, work, what, busy));
        }

        // Waited on, so that a pass does not report on work it has not finished, and does not overlap
        // whatever the caller does next with what it is still doing.
        CompletableFuture.allOf(inFlight.toArray(new CompletableFuture[0])).join();
        return busy;
    }

    private CompletableFuture<Void> start(final int shard,
                                          final IntFunction<String> lockName,
                                          final IntConsumer work,
                                          final String what,
                                          final Set<Integer> busy) {
        return CompletableFuture
                // Each shard re-establishes the processing user: the identity is held per thread, so
                // it does not travel to a pool thread on its own.
                .runAsync(() -> securityContext.asProcessingUser(() -> {
                    // Whether the work ran, because tryLock gives nothing back and a shard that was
                    // busy looks exactly like one that had nothing to do.
                    final boolean[] ran = {false};
                    clusterLockService.tryLock(lockName.apply(shard), () -> {
                        ran[0] = true;
                        work.accept(shard);
                    });
                    if (!ran[0]) {
                        busy.add(shard);
                    }
                }), executor)
                .exceptionally(t -> {
                    // One shard failing is not the pass failing: the others are independent of it and
                    // this one is asked for again next time round.
                    LOGGER.error(() -> LogUtil.message("Error {} shard {}: {}",
                            what, shard, t.getMessage()), t);
                    return null;
                });
    }
}
