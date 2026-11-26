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

package stroom.index.impl;

import stroom.security.api.SecurityContext;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

/// The work of the one-minute job *Index Shard Stream Recovery*, which any node can do as it only uses the
/// database. For now it only takes shards from nodes that have stopped renewing their leases.
@Singleton
public class IndexShardStreamRecovery {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(IndexShardStreamRecovery.class);

    private final IndexShardDao indexShardDao;
    private final SecurityContext securityContext;
    private final Provider<IndexWriterConfig> indexWriterConfigProvider;

    @Inject
    IndexShardStreamRecovery(final IndexShardDao indexShardDao,
                             final SecurityContext securityContext,
                             final Provider<IndexWriterConfig> indexWriterConfigProvider) {
        this.indexShardDao = indexShardDao;
        this.securityContext = securityContext;
        this.indexWriterConfigProvider = indexWriterConfigProvider;
    }

    /// Runs every part of the job once. The entry point of the job.
    public void exec() {
        securityContext.asProcessingUser(() -> run("releaseLapsedLeases", this::releaseLapsedLeases));
    }

    private void run(final String name, final Runnable runnable) {
        try {
            LOGGER.logDurationIfDebugEnabled(runnable, name);
        } catch (final RuntimeException e) {
            LOGGER.error(() -> "Error in index shard stream recovery (" + name + "): " + e.getMessage(), e);
        }
    }

    /// Takes shards on shared volumes from nodes that stopped renewing their leases long enough ago, e.g.
    /// because they died. The shards are left closed, for any node to write to.
    void releaseLapsedLeases() {
        final IndexWriterConfig config = indexWriterConfigProvider.get();
        final int count = indexShardDao.releaseLapsedLeases(
                config.getShardLeaseTakeoverGrace().toMillis(),
                config.getReplacedCommitRetention().toMillis());
        if (count > 0) {
            LOGGER.warn(() -> LogUtil.message(
                    "Took {} index shard(s) from nodes whose leases on them expired more than {} ago. The " +
                    "documents those nodes had not committed have been lost.",
                    count, config.getShardLeaseTakeoverGrace()));
        }
    }
}
