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

package stroom.util.concurrent;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * <p>
 * Useful when you need a value supplier to update the value supplied based on some condition,
 * but you don't want to incur the cost of checking that condition on each call.
 * </p>
 * <p>
 * This will only call hasStateChangedCheck if the time since the last check is
 * greater than checkInterval. If hasStateChangedCheck returns true then
 * it will call valueSupplier to obtain a new value.
 * </p>
 * <p>
 * You have the option of calling {@link CachedValue#getValue()} which may trigger a synchronous
 * update of the value if old, or {@link CachedValue#getValueAsync()} which may trigger an
 * asynchronous update and return the current value without waiting.
 * </p>
 *
 * @param <V> The type of the value.
 * @param <S> The type of the state used to determine if the value needs to be updated.
 */
public class CachedValue<V, S> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(CachedValue.class);
    private static final int UNINITIALISED = 0;

    private final long checkIntervalMs;
    private final BiFunction<S, V, V> valueSupplier;
    private final Supplier<S> stateSupplier;
    private final AtomicBoolean isAsyncUpdateInProgress = new AtomicBoolean(false);

    private final AtomicLong nextCheckEpochMs = new AtomicLong(UNINITIALISED);
    private final Executor executor;
    private volatile S state = null;
    private volatile V value = null;

    /**
     * State dependant version where we only want to update the value if the state has changed.
     *
     * @param maxCheckInterval The max interval to call stateSupplier to determine if the valueSupplier needs
     *                         to be called to update the value.
     * @param valueSupplier    Will be called on first call of {@link CachedValue#getValue()}
     *                         then any time the value supplied by stateSupplier changes. If multiple
     *                         threads call {@link CachedValue#getValue()} at once valueSupplier
     *                         may be called by each thread. Should be side effect free.
     * @param stateSupplier    Will generally be called once per checkInterval unless multiple threads
     *                         call {@link CachedValue#getValue()} at once, in which case
     *                         it may be called by multiple threads at once. Should be side effect free.
     *                         Value returned must implement equals method to determine if it has changed.
     */
    private CachedValue(final Duration maxCheckInterval,
                        final BiFunction<S, V, V> valueSupplier,
                        final Supplier<S> stateSupplier,
                        final Executor executor) {
        this.checkIntervalMs = Objects.requireNonNull(maxCheckInterval).toMillis();
        this.valueSupplier = Objects.requireNonNull(valueSupplier);
        this.stateSupplier = stateSupplier;
        // isAsyncUpdateInProgress prevents two threads from doing an async update concurrently
        // so use a newSingleThreadExecutor by default
        this.executor = Objects.requireNonNullElseGet(executor, Executors::newSingleThreadExecutor);
    }

    private CheckResult<S> checkUpdateRequired() {
        final long nextCheckEpochMsVal = nextCheckEpochMs.get();
        if (nextCheckEpochMsVal == UNINITIALISED) {
            LOGGER.debug("Uninitialised");
            return CheckResult.initialiseRequired(NullSafe.get(stateSupplier, Supplier::get));
        } else {
            final long nowMs = System.currentTimeMillis();
            LOGGER.debug(() -> LogUtil.message("oldNextCheck: {}, nowMs: {}, delta: {}",
                    Instant.ofEpochMilli(nextCheckEpochMsVal),
                    Instant.ofEpochMilli(nowMs),
                    Duration.between(Instant.ofEpochMilli(nextCheckEpochMsVal), Instant.ofEpochMilli(nowMs))));

            if (nowMs > nextCheckEpochMsVal) {
                if (stateSupplier == null) {
                    // No state to check
                    LOGGER.debug("Stateless update required");
                    return CheckResult.updateRequired(null);
                } else {
                    final S newState = stateSupplier.get();
                    if (Objects.equals(state, newState)) {
                        // No state change
                        LOGGER.debug("State un-changed");
                        return CheckResult.noUpdateRequired();
                    } else {
                        LOGGER.debug("Stateful update required");
                        return CheckResult.updateRequired(newState);
                    }
                }
            } else {
                return CheckResult.noUpdateRequired();
            }
        }
    }

    /**
     * Gets the updatable value. If it has not been updated for longer than maxCheckInterval
     * or is uninitialised, then the state will be fetched and, if the state has changed, the value will be
     * updated synchronously.
     * <p>
     * If this is a stateless {@link CachedValue} then an update will happen if the value is
     * uninitialised or the time since last updated is greater than maxCheckInterval.
     * </p>
     */
    public V getValue() {
        // If uninitialised or we have passed the next check time then get the state
        // to see if it has changed. If it has then update the value.
        if (checkUpdateRequired().isUpdateRequired) {
            // Re-check under lock and update if required
            checkAndUpdateUnderLock();
        } else {
            LOGGER.debug("getValue() - No update required");
        }
        LOGGER.debug("getValue() - Returning {}", value);
        return value;
    }

    /**
     * Gets the updatable value. If it has not been updated for longer than maxCheckInterval
     * then it will trigger an asynchronous check of the state and potential update of the value.
     * <p>
     * This method will always return the current known value WITHOUT waiting for the async update to happen.
     * However, if the value is uninitialised, a synchronous update will happen.
     * </p>
     */
    public V getValueAsync() {
        // If uninitialised or we have passed the next check time then get the state
        // to see if it has changed. If it has then update the value.
        final CheckResult<S> checkResult = checkUpdateRequired();
        if (!checkResult.isValueInitialised) {
            // Value is not initialised, so we are forced to do it synchronously
            checkAndUpdateUnderLock();
        } else if (checkResult.isUpdateRequired) {
            // No point us updating if another thread has kicked one off
            if (isAsyncUpdateInProgress.compareAndSet(false, true)) {
                LOGGER.debug("getValueAsync() - Creating an async task to check and update under lock");
                // It is possible that when the async code runs another thread may
                // have already updated the value, so it will do nothing
                CompletableFuture.runAsync(() -> {
                    try {
                        checkAndUpdateUnderLock();
                        LOGGER.debug("getValueAsync() - Completed async task");
                    } catch (final Throwable e) {
                        LOGGER.error("getValueAsync() - Error running async checkAndUpdateUnderLock(): {}",
                                LogUtil.exceptionMessage(e), e);
                    } finally {
                        isAsyncUpdateInProgress.set(false);
                    }
                }, executor);
            } else {
                LOGGER.debug("getValueAsync() - Another thread has initiated an update");
            }
        } else {
            LOGGER.debug("getValueAsync() - No update required");
        }
        LOGGER.debug("getValueAsync() - Returning {}", value);
        // Return the value held now without waiting for the async update to complete
        return value;
    }

    private void checkAndUpdateUnderLock() {
        synchronized (this) {
            // Recheck under lock
            final CheckResult<S> checkResult = checkUpdateRequired();
            if (checkResult.isUpdateRequired) {
                final V newValue = valueSupplier.apply(checkResult.newState, value);
                LOGGER.debug(() -> LogUtil.message(
                        "Updating value - nextCheck: {}, state: {}, newState: {}, value: {}, newValue: {}",
                        Instant.ofEpochMilli(nextCheckEpochMs.get()), state, checkResult.newState, value, newValue));
                state = checkResult.newState;
                value = newValue;
                final long newNextCheckEpochMs = System.currentTimeMillis() + checkIntervalMs;
                LOGGER.debug(() -> LogUtil.message("newNextCheckEpochMs: {}", newNextCheckEpochMs));
                nextCheckEpochMs.set(newNextCheckEpochMs);
            } else {
                LOGGER.debug("No update required (under lock)");
            }
        }
    }

    /**
     * Get the state value used to determine if the value needs to be updated or not.
     */
    public S getState() {
        // Call getValue to ensure that the state as been initialised and is up-to-date
        getValue();
        return state;
    }

    public static BuilderStage1 builder() {
        return new BuilderStage1();
    }


    // --------------------------------------------------------------------------------


    public static class BuilderStage1 {


        private BuilderStage1() {
        }

        /**
         * @param maxCheckInterval The max interval to call stateSupplier to determine if the valueSupplier needs
         *                         to be called to update the value.
         */
        public BuilderStage2 withMaxCheckInterval(final Duration maxCheckInterval) {
            return new BuilderStage2(Objects.requireNonNull(maxCheckInterval));
        }

        /**
         * @param maxCheckIntervalMinutes The max interval to call stateSupplier to determine if the
         *                                valueSupplier needs to be called to update the value.
         */
        public BuilderStage2 withMaxCheckIntervalMinutes(final int maxCheckIntervalMinutes) {
            return new BuilderStage2(Duration.ofMinutes(maxCheckIntervalMinutes));
        }

        /**
         * @param maxCheckIntervalSeconds The max interval to call stateSupplier to determine if the
         *                                valueSupplier needs to be called to update the value.
         */
        public BuilderStage2 withMaxCheckIntervalSeconds(final int maxCheckIntervalSeconds) {
            return new BuilderStage2(Duration.ofSeconds(maxCheckIntervalSeconds));
        }

        /**
         * @param maxCheckIntervalMillis The max interval to call stateSupplier to determine if the
         *                               valueSupplier needs to be called to update the value.
         */
        public BuilderStage2 withMaxCheckIntervalMillis(final int maxCheckIntervalMillis) {
            return new BuilderStage2(Duration.ofMillis(maxCheckIntervalMillis));
        }
    }


    // --------------------------------------------------------------------------------


    public static class BuilderStage2 {

        private final Duration maxCheckInterval;

        private BuilderStage2(final Duration maxCheckInterval) {
            this.maxCheckInterval = maxCheckInterval;
        }

        /**
         * Use this method when your cached value is dependent on state, e.g. the state supplies the variables
         * for constructing an object that is expensive to construct. For a given state, the value should always
         * be the same, i.e. if the state does not change, the value also won't.
         *
         * @param stateSupplier Will generally be called once per checkInterval unless multiple threads
         *                      call {@link CachedValue#getValue()} at once, in which case
         *                      it may be called by multiple threads at once. Should be side effect free.
         *                      Value returned must implement equals method to determine if it has changed.
         *                      If the state returned has not changed since the last time it was supplied,
         *                      no update to the value will happen.
         */
        public <S> BuilderStage3a<S> withStateSupplier(final Supplier<S> stateSupplier) {
            return new BuilderStage3a<>(this, stateSupplier);
        }

        /**
         * Updating the value is not dependent on any state, only the time since the last
         * update.
         */
        public BuilderStage3b withoutStateSupplier() {
            return new BuilderStage3b(this);
        }
    }


    // --------------------------------------------------------------------------------


    public static class BuilderStage3a<S> {

        private final BuilderStage2 builderStage2;
        private final Supplier<S> stateSupplier;

        private BuilderStage3a(final BuilderStage2 builderStage2,
                               final Supplier<S> stateSupplier) {
            this.builderStage2 = builderStage2;
            this.stateSupplier = stateSupplier;
        }

        /**
         * @param valueFunction Will be called on first call of {@link CachedValue#getValue()}
         *                      then any time the value supplied by stateSupplier changes. If multiple
         *                      threads call {@link CachedValue#getValue()} at once valueSupplier
         *                      may be called by each thread. Should be side effect free.
         */
        public <V> BuilderStage4<S, V> withValueFunction(final Function<S, V> valueFunction) {
            return new BuilderStage4<>(
                    builderStage2.maxCheckInterval,
                    stateSupplier,
                    (state, ignored) ->
                            valueFunction.apply(state));
        }

        /**
         * @param valueFunction Will be called on first call of {@link CachedValue#getValue()}
         *                      then any time the value supplied by stateSupplier changes. If multiple
         *                      threads call {@link CachedValue#getValue()} at once valueSupplier
         *                      may be called by each thread. Should be side effect free.
         *                      <p>
         *                      The current state and value will be passed into valueFunction. In the
         *                      event of an error in valueFunction the current value can be used in place
         *                      of a new value.
         *                      </p>
         */
        public <V> BuilderStage4<S, V> withValueFunction(final BiFunction<S, V, V> valueFunction) {
            return new BuilderStage4<>(
                    builderStage2.maxCheckInterval,
                    stateSupplier,
                    valueFunction);
        }
    }


    // --------------------------------------------------------------------------------


    public static class BuilderStage3b {

        private final BuilderStage2 builderStage2;

        private BuilderStage3b(final BuilderStage2 builderStage2) {
            this.builderStage2 = builderStage2;
        }

        /**
         * @param valueSupplier Will be called on first call of {@link CachedValue#getValue()}
         *                      then any time the value supplied by stateSupplier changes. If multiple
         *                      threads call {@link CachedValue#getValue()} at once valueSupplier
         *                      may be called by each thread. Should be side effect free.
         */
        public <V> BuilderStage4<Void, V> withValueSupplier(final Supplier<V> valueSupplier) {
            return new BuilderStage4<>(
                    builderStage2.maxCheckInterval,
                    null,
                    (Void ignoredState, V ignoredVal) ->
                            valueSupplier.get());
        }

        /**
         * @param valueSupplier Will be called on first call of {@link CachedValue#getValue()}
         *                      then any time the value supplied by stateSupplier changes. If multiple
         *                      threads call {@link CachedValue#getValue()} at once valueSupplier
         *                      may be called by each thread. Should be side effect free.
         */
        public <V> BuilderStage4<Void, V> withValueSupplier(final Function<V, V> valueSupplier) {
            return new BuilderStage4<>(
                    builderStage2.maxCheckInterval,
                    null,
                    (Void ignoredState, V currVal) ->
                            valueSupplier.apply(currVal));
        }
    }


    // --------------------------------------------------------------------------------


    public static class BuilderStage4<S, V> {

        private final Duration maxCheckInterval;
        private final Supplier<S> stateSupplier;
        private final BiFunction<S, V, V> valueFunction;
        private Executor executor;

        private BuilderStage4(final Duration maxCheckInterval,
                              final Supplier<S> stateSupplier,
                              final BiFunction<S, V, V> valueFunction) {
            this.maxCheckInterval = maxCheckInterval;
            this.stateSupplier = stateSupplier;
            this.valueFunction = valueFunction;
        }

        /**
         * Optionally provide an executor to use with {@link CachedValue#getValueAsync()}, e.g.
         * to use a cached thread pool. If not provided, {@link Executors#newSingleThreadExecutor()}
         * will be used by default.
         */
        public BuilderStage4<S, V> withExecutor(final Executor executor) {
            this.executor = executor;
            return this;
        }

        public CachedValue<V, S> build() {
            return new CachedValue<>(maxCheckInterval, valueFunction, stateSupplier, executor);
        }
    }


    // --------------------------------------------------------------------------------


    private record CheckResult<S>(boolean isUpdateRequired,
                                  boolean isValueInitialised,
                                  S newState) {

        private static <S> CheckResult<S> initialiseRequired(final S state) {
            return new CheckResult<>(true, false, state);
        }

        private static <S> CheckResult<S> updateRequired(final S state) {
            return new CheckResult<>(true, true, state);
        }

        private static <S> CheckResult<S> noUpdateRequired() {
            return new CheckResult<>(false, true, null);
        }
    }
}
