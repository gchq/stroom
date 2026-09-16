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

package stroom.proxy.app.pipeline.stage.forward;

import stroom.proxy.app.execution.Loop;
import stroom.proxy.app.handler.LivenessCheck;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import java.util.Objects;

/**
 * Runs a destination's liveness check on a schedule and pauses the destination's loop while the
 * check fails, resuming it when it passes. A paused loop holds no claims: the groups wait on the
 * queue, claimable by any node that is not paused ({@code designs/stages/forward.md} F6, §4.2).
 */
public final class LivenessWatch implements Runnable {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(LivenessWatch.class);

    private final String destinationName;
    private final LivenessCheck check;
    private final Loop loop;
    private volatile Boolean lastLive;

    public LivenessWatch(final String destinationName, final LivenessCheck check, final Loop loop) {
        this.destinationName = Objects.requireNonNull(destinationName, "destinationName");
        this.check = Objects.requireNonNull(check, "check");
        this.loop = Objects.requireNonNull(loop, "loop");
    }

    @Override
    public void run() {
        String reason = null;
        boolean live;
        try {
            check.check();
            live = true;
        } catch (final Exception e) {
            LOGGER.debug("'{}' - liveness check failed", destinationName, e);
            live = false;
            reason = e.getMessage();
        }

        final Boolean previous = lastLive;
        lastLive = live;
        if (live) {
            if (previous == null || !previous) {
                LOGGER.info(() -> LogUtil.message(
                        "'{}' - liveness check passed, forwarding to it", destinationName));
            }
            loop.resume();
        } else {
            final String why = reason;
            if (previous == null || previous) {
                LOGGER.warn(() -> LogUtil.message(
                        "'{}' - liveness check failed, pausing forwarding to it: {}", destinationName, why));
            } else {
                LOGGER.warn(() -> LogUtil.message(
                        "'{}' - liveness check still failing: {}", destinationName, why));
            }
            loop.pause();
        }
    }

    /**
     * @return null before the first check, then the last result.
     */
    public Boolean lastLive() {
        return lastLive;
    }
}
