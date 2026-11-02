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

package stroom.ai.impl.tools;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.function.LongSupplier;

/// What a long tool call is doing, told to its client as it goes, through the call's [ToolProgress]:
/// `CallProgress.of(context).say("Searching ...")`. A client that asked for progress shows each message while the
/// call runs; for any other, nothing is sent. Asked for by the user: a survey of an index ran for minutes with
/// nothing to show what it was doing.
///
/// There is one for the whole call, wherever in it it is asked for, so [#nowAndThen] counts from whatever was said
/// last in the call. It never fails the call: a message that can't be sent is logged and dropped.
public final class CallProgress {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(CallProgress.class);

    /// The time between a loop's messages ([#nowAndThen(String)]).
    public static final Duration EVERY = Duration.ofSeconds(5);

    private static final Map<ToolCallContext, CallProgress> CALLS = Collections.synchronizedMap(new WeakHashMap<>());

    private final ToolProgress progress;
    private final LongSupplier clock;
    private long saidMs = Long.MIN_VALUE;

    /// @param progress Where the call reports progress.
    /// @param clock    The time now, in milliseconds (for tests).
    CallProgress(final ToolProgress progress, final LongSupplier clock) {
        this.progress = Objects.requireNonNullElse(progress, ToolProgress.NONE);
        this.clock = clock;
    }

    /// @param context A call's context.
    /// @return The call's progress: the same one each time it is asked for in the call.
    public static CallProgress of(final ToolCallContext context) {
        return CALLS.computeIfAbsent(context, found -> new CallProgress(found.progress(), System::currentTimeMillis));
    }

    /// Tells the client what the call is doing now.
    ///
    /// @param message What the call is doing, e.g. "Stepping stream 7 (1 of 3) through ACME".
    public synchronized void say(final String message) {
        saidMs = clock.getAsLong();
        try {
            progress.report(message);
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("Progress not sent ({}): {}", message, e.getMessage()), e);
        }
    }

    /// Says the message only when nothing has been said for [#EVERY]: for each turn of a loop (a record stepped),
    /// so a quick loop says nothing and a slow one says where it is now and then, not at every turn.
    ///
    /// @param message Where the loop is.
    public void nowAndThen(final String message) {
        nowAndThen(message, EVERY);
    }

    /// As [#nowAndThen(String)], with the time between messages given.
    ///
    /// @param message Where the loop is.
    /// @param every   The least time between messages.
    public synchronized void nowAndThen(final String message, final Duration every) {
        if (saidMs == Long.MIN_VALUE || clock.getAsLong() - saidMs >= every.toMillis()) {
            say(message);
        }
    }
}
