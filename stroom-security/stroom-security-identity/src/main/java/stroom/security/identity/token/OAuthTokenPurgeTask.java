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

package stroom.security.identity.token;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Deletes expired rows from the token inventory.
 * <p>
 * Housekeeping only. Every read in {@link OAuthTokenDao} filters on expiry, so an unswept row is never
 * honoured and correctness does not depend on this job running - it reclaims space rather than enforcing
 * anything. That property is deliberate: a purge job that fails or is disabled must not turn into a security
 * problem.
 * </p>
 */
@Singleton
public class OAuthTokenPurgeTask {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(OAuthTokenPurgeTask.class);

    private final OAuthTokenDao oAuthTokenDao;

    @Inject
    OAuthTokenPurgeTask(final OAuthTokenDao oAuthTokenDao) {
        this.oAuthTokenDao = oAuthTokenDao;
    }

    public void exec() {
        final long now = System.currentTimeMillis();
        final int deleted = oAuthTokenDao.deleteExpired(now);
        if (deleted > 0) {
            LOGGER.info(() -> "Purged " + deleted + " expired token inventory row(s)");
        } else {
            LOGGER.debug("No expired token inventory rows to purge");
        }
    }
}
