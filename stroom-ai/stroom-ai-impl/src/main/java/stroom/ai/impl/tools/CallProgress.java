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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

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
    // Says that long work is still going on (see whileWorking), on one daemon thread for every call: each message is
    // short, and one that can't be sent is dropped.
    private static final ScheduledExecutorService STILL_WORKING = Executors.newSingleThreadScheduledExecutor(
            runnable -> {
                final Thread thread = new Thread(runnable, "Tool call progress");
                thread.setDaemon(true);
                return thread;
            });

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

    /// Does long work that says nothing as it goes (e.g. a check of a large sample), saying the message first and
    /// then, every [#EVERY] until it ends, that it is still working: a client that gives up on a call after a time
    /// with nothing sent on it, unless it is told how the call is getting on, waits (seen in eval case 36 on Haiku
    /// over Claude Code: build_translation_xslt checked a 1,000-record sample for 61 s and the client gave up).
    ///
    /// @param message What the work is, e.g. "Checking the mapping against 1000 sample records".
    /// @param work    The work, done on this thread.
    /// @param <T>     What it gives.
    /// @return What it gave.
    public <T> T whileWorking(final String message, final Supplier<T> work) {
        return whileWorking(message, EVERY, work);
    }

    /// As [#whileWorking(String, Supplier)], with the time between messages given.
    ///
    /// @param message What the work is.
    /// @param every   The time between messages.
    /// @param work    The work, done on this thread.
    /// @param <T>     What it gives.
    /// @return What it gave.
    public <T> T whileWorking(final String message, final Duration every, final Supplier<T> work) {
        say(message);
        final long startMs = clock.getAsLong();
        final AtomicBoolean ended = new AtomicBoolean();
        final ScheduledFuture<?> stillWorking = STILL_WORKING.scheduleAtFixedRate(() -> {
            // Under this one's lock, as the end is marked: nothing is said once the work has ended.
            synchronized (this) {
                if (!ended.get()) {
                    say(message + ": still working (" + Duration.ofMillis(clock.getAsLong() - startMs)
                            .toSeconds() + " s)");
                }
            }
        }, every.toMillis(), every.toMillis(), TimeUnit.MILLISECONDS);
        try {
            return work.get();
        } finally {
            synchronized (this) {
                ended.set(true);
            }
            stillWorking.cancel(false);
        }
    }
}
