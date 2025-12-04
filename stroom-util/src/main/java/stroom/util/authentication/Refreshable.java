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

package stroom.util.authentication;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Something with an expiry time that can be refreshed before or after the expiry time is
 * reached to renew it and move the expiry time into the future.
 */
public interface Refreshable extends HasExpiry {

    LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(Refreshable.class);

    /**
     * Factor to increase the refresh buffer by for eager refreshes.
     */
    double EAGER_REFRESH_BUFFER_FACTOR = 1.1;

    /**
     * @return An ID, unique on this stroom node, that identifies this refreshable wrapper,
     * not the state that gets refreshed. I.e. the uuid stays the same after a refresh even
     * though the underlying value has changed.
     */
    String getUuid();

    /**
     * @return False if the {@link Refreshable} is no longer needed, e.g. a token for a user whose session
     * has expired, therefore no more refreshes will be required, ever.
     */
    default boolean isActive() {
        return true;
    }

    /**
     * @return True if the {@link Refreshable#getExpireTimeWithBufferEpochMs(RefreshMode)} has passed.
     */
    default boolean isRefreshRequired(final RefreshMode refreshMode) {
        final boolean isRefreshRequired = System.currentTimeMillis() >= getExpireTimeWithBufferEpochMs(refreshMode);
        LOGGER.trace("isRefreshRequired: {}", isRefreshRequired);
        return isRefreshRequired;
    }

    /**
     * Refresh this {@link Refreshable}.
     *
     * @return True if the refresh happened
     */
    default boolean refresh() {
        return refresh(null);
    }

    /**
     * Refresh this {@link Refreshable}, calling onRefreshAction if the refresh happened.
     *
     * @param onRefreshAction An optional callback to run if a refresh happens.
     * @return True if the refresh happened
     */
    boolean refresh(final Consumer<Refreshable> onRefreshAction);

    /**
     * Refresh this {@link Refreshable} if {@link Refreshable#isRefreshRequired(RefreshMode)} is true.
     *
     * @return True if the refresh happened
     */
    default boolean refreshIfRequired(final RefreshMode refreshMode) {
        return refreshIfRequired(refreshMode, null);
    }

    /**
     * Refresh this {@link Refreshable} if {@link Refreshable#isRefreshRequired(RefreshMode)} is true.
     * Calls onRefreshAction if the refresh happened.
     *
     * @param onRefreshAction An optional callback to run if a refresh happens.
     * @return True if the refresh happened
     */
    default boolean refreshIfRequired(final RefreshMode refreshMode,
                                      final Consumer<Refreshable> onRefreshAction) {
        final boolean didRefresh;
        if (isRefreshRequired(refreshMode)) {
            synchronized (this) {
                if (isRefreshRequired(refreshMode)) {
                    if (RefreshMode.JUST_IN_TIME == refreshMode) {
                        // If things are working properly then the RefreshManager should have refreshed it
                        // in EAGER mode before we need to do it in JUST_IN_TIME mode.
                        LOGGER.debug(() -> LogUtil.message(
                                "Just in time refresh required for type: {}, expireTime: {}, refreshBuffer: {}",
                                this.getClass().getSimpleName(),
                                LogUtil.instant(getExpireTimeEpochMs()),
                                Duration.ofMillis(getRefreshBufferMs())));
                    }
                    didRefresh = refresh(onRefreshAction);
                    LOGGER.trace("Refreshing, didRefresh: {}", didRefresh);
                } else {
                    LOGGER.trace("Refresh not required");
                    didRefresh = false;
                }
            }
        } else {
            LOGGER.trace("Refresh not required");
            didRefresh = false;
        }
        return didRefresh;
    }

    /**
     * @return The actual hard expiry time of this item.
     */
    @Override
    long getExpireTimeEpochMs();

    /**
     * @return The time prior to the expiry time after which a refresh is required.
     * This buffer ensures the token has enough life remaining for the user of the token
     * to successfully use it.
     */
    default long getExpireTimeWithBufferEpochMs(final RefreshMode refreshMode) {
        final long refreshBufferMs = Math.max(0, getRefreshBufferMs());
        return switch (Objects.requireNonNull(refreshMode)) {
            case EAGER -> {
                final long effectiveBufferMs;
                if (refreshBufferMs > 0) {
                    effectiveBufferMs = (long) (EAGER_REFRESH_BUFFER_FACTOR * refreshBufferMs);
                } else {
                    effectiveBufferMs = 0;
                }
                yield getExpireTimeEpochMs() - effectiveBufferMs;
            }
            case JUST_IN_TIME -> getExpireTimeEpochMs() - refreshBufferMs;
        };
    }

    /**
     * @return The size of the buffer to use before the actual hard expiry time is reached
     * so that the refresh happens before expiry.
     * This buffer ensures the token has enough life remaining for the user of the token
     * to successfully use it.
     */
    default long getRefreshBufferMs() {
        return 0;
    }


    // --------------------------------------------------------------------------------


    enum RefreshMode {
        /**
         * Refresh prior to {@link Refreshable#getExpireTimeWithBufferEpochMs(RefreshMode)} being passed, adding
         * {@link Refreshable#EAGER_REFRESH_BUFFER_FACTOR} to the buffer.
         */
        EAGER,
        /**
         * Refresh when {@link Refreshable#getExpireTimeWithBufferEpochMs(RefreshMode)} has passed.
         */
        JUST_IN_TIME;
    }
}
