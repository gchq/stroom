package stroom.util.concurrent;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
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
 *
 * @param <V> The type of the value.
 * @param <S> The type of the state used to determine if the value needs to be updated.
 */
public class CachedValue<V, S> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(CachedValue.class);
    private static final int UNINITIALISED = 0;

    private final long checkIntervalMs;
    private final Function<S, V> valueSupplier;
    private final Supplier<S> stateSupplier;

    private final AtomicLong nextCheckEpochMs = new AtomicLong(UNINITIALISED);
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
                        final Function<S, V> valueSupplier,
                        final Supplier<S> stateSupplier) {
        this.checkIntervalMs = Objects.requireNonNull(maxCheckInterval).toMillis();
        this.valueSupplier = Objects.requireNonNull(valueSupplier);
        this.stateSupplier = stateSupplier;
    }

    private CheckResult<S> checkUpdateRequired() {
        final long nowMs = System.currentTimeMillis();
        if (nowMs == UNINITIALISED) {
            LOGGER.debug("Uninitialised");
            return CheckResult.updateRequired(NullSafe.get(stateSupplier, Supplier::get));
        } else {
            final long oldNextCheck = nextCheckEpochMs.get();
            LOGGER.debug("oldNextCheck: {}, nowMs: {}", oldNextCheck, nowMs);
            if (nowMs > oldNextCheck) {
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
     * then the state will be fetched and, if the state has changed, the value will be
     * updated synchronously.
     */
    public V getValue() {
        // If uninitialised or we have passed the next check time then get the state
        // to see if it has changed. If it has then update the value.
        if (checkUpdateRequired().isUpdateRequired) {
            synchronized (this) {
                // Recheck under lock
                final CheckResult<S> checkResult = checkUpdateRequired();
                if (checkResult.isUpdateRequired) {
                    final V newValue = valueSupplier.apply(checkResult.newState);
                    LOGGER.debug(() -> LogUtil.message(
                            "Updating value - nextCheck: {}, state: {}, newState: {}, value: {}, newValue: {}",
                            nextCheckEpochMs.get(), state, checkResult.newState, value, newValue));
                    state = checkResult.newState;
                    value = newValue;
                    nextCheckEpochMs.set(System.currentTimeMillis() + checkIntervalMs);
                }
            }
        }
        LOGGER.debug("Returning {}", value);
        return value;
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
         * @param stateSupplier Will generally be called once per checkInterval unless multiple threads
         *                      call {@link CachedValue#getValue()} at once, in which case
         *                      it may be called by multiple threads at once. Should be side effect free.
         *                      Value returned must implement equals method to determine if it has changed.
         */
        public <S> BuilderStage3a<S> withStateSupplier(final Supplier<S> stateSupplier) {
            return new BuilderStage3a<>(this, stateSupplier);
        }

        /**
         * Updating the value is not dependent on any state.
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
                    (Void ignored) -> valueSupplier.get());
        }
    }


    // --------------------------------------------------------------------------------


    public static class BuilderStage4<S, V> {

        private final Duration maxCheckInterval;
        private final Supplier<S> stateSupplier;
        private final Function<S, V> valueFunction;

        private BuilderStage4(final Duration maxCheckInterval,
                              final Supplier<S> stateSupplier,
                              final Function<S, V> valueFunction) {
            this.maxCheckInterval = maxCheckInterval;
            this.stateSupplier = stateSupplier;
            this.valueFunction = valueFunction;
        }

        public CachedValue<V, S> build() {
            return new CachedValue<>(maxCheckInterval, valueFunction, stateSupplier);
        }
    }


    // --------------------------------------------------------------------------------


    private record CheckResult<S>(boolean isUpdateRequired,
                                  S newState) {

        private static <S> CheckResult<S> updateRequired(final S state) {
            return new CheckResult<>(true, state);
        }

        private static <S> CheckResult<S> noUpdateRequired() {
            return new CheckResult<>(false, null);
        }
    }
}
