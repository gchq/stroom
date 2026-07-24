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

package stroom.security.identity.token;

import stroom.security.identity.config.TokenConfig;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.time.Duration;

/**
 * Rotates the internal identity provider's signing key on a schedule.
 * <p>
 * This runs the rotation off the read path deliberately. {@link JwkCache} refreshes on every node
 * once a minute, so doing the rotation check there would have the cluster stampede on writes; a
 * scheduled job does it at a sensible cadence. The rotation itself is lockless and safe to run on
 * several nodes at once, see {@link JwkDao#rotate}, so this job needs no coordination of its own.
 * </p>
 */
@Singleton
class JwkRotationTask {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(JwkRotationTask.class);

    private final JwkDao jwkDao;
    private final Provider<TokenConfig> tokenConfigProvider;
    private final TaskContextFactory taskContextFactory;

    @Inject
    JwkRotationTask(final JwkDao jwkDao,
                    final Provider<TokenConfig> tokenConfigProvider,
                    final TaskContextFactory taskContextFactory) {
        this.jwkDao = jwkDao;
        this.tokenConfigProvider = tokenConfigProvider;
        this.taskContextFactory = taskContextFactory;
    }

    public void exec() {
        final TaskContext taskContext = taskContextFactory.current();
        taskContext.info(() -> "Rotating identity provider signing keys");

        final TokenConfig tokenConfig = tokenConfigProvider.get();
        final Duration rotationInterval = tokenConfig.getJwkRotationInterval().getDuration();
        final Duration retention = tokenConfig.getJwkRetention();

        final JwkRotationSummary summary = jwkDao.rotate(rotationInterval, retention);

        if (summary.createdKeyId() != null && summary.retiredKeyId() != null) {
            LOGGER.info(() -> LogUtil.message(
                    "Rotated identity signing key: now signing with {}, retired {}, "
                            + "reconciled {} surplus, deleted {} expired key(s)",
                    summary.createdKeyId(), summary.retiredKeyId(),
                    summary.reconciledCount(), summary.deletedCount()));
        } else if (summary.createdKeyId() != null) {
            LOGGER.info(() -> LogUtil.message(
                    "Created initial identity signing key {}; reconciled {} surplus, deleted {} expired key(s)",
                    summary.createdKeyId(), summary.reconciledCount(), summary.deletedCount()));
        } else if (summary.reconciledCount() > 0 || summary.deletedCount() > 0) {
            LOGGER.info(() -> LogUtil.message(
                    "Active identity signing key unchanged; reconciled {} surplus, deleted {} expired key(s)",
                    summary.reconciledCount(), summary.deletedCount()));
        } else {
            LOGGER.debug("No identity signing key rotation needed");
        }
    }
}
