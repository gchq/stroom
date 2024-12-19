package stroom.util.concurrent;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

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
 * This will only call hasStateChangedCheck every checkInterval. If hasStateChangedCheck
 * returns true then it will call valueSupplier to obtain a new value.
 * </p>
 *
 * @param <V> The type of the value.
 * @param <S> The type of the state used to determine if the value needs to be updated.
 */
public class PeriodicallyUpdatedValue<V, S> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(PeriodicallyUpdatedValue.class);
    private static final int UNINITIALISED = 0;

    private final long checkIntervalMs;
    private final Function<S, V> valueSupplier;
    private final Supplier<S> stateSupplier;

    private final AtomicLong nextCheckEpochMs = new AtomicLong(UNINITIALISED);
    private S state = null;
    private V value = null;

    /**
     * @param maxCheckInterval The max interval to call stateSupplier to determine if the valueSupplier needs
     *                         to be called to update the value.
     * @param valueSupplier    Will be called on first call of {@link PeriodicallyUpdatedValue#getValue()}
     *                         then any time the value supplied by stateSupplier changes. If multiple
     *                         threads call {@link PeriodicallyUpdatedValue#getValue()} at once valueSupplier
     *                         may be called by each thread. Should be side effect free.
     * @param stateSupplier    Will generally be called once per checkInterval unless multiple threads
     *                         call {@link PeriodicallyUpdatedValue#getValue()} at once, in which case
     *                         it may be called by multiple threads at once. Should be side effect free.
     *                         Value returned must implement equals method to determine if it has changed.
     */
    public PeriodicallyUpdatedValue(final Duration maxCheckInterval,
                                    final Function<S, V> valueSupplier,
                                    final Supplier<S> stateSupplier) {
        this.checkIntervalMs = Objects.requireNonNull(maxCheckInterval).toMillis();
        this.valueSupplier = Objects.requireNonNull(valueSupplier);
        this.stateSupplier = Objects.requireNonNull(stateSupplier);
    }

    /**
     * Gets the updatable value. If it has not been updated for longer than maxCheckInterval
     * then the state will be fetched and the value updated if the state has changed.
     */
    public V getValue() {
        final long oldNextCheck = nextCheckEpochMs.get();
        LOGGER.debug("getValue(), value: {}, nextCheck: {}", value, oldNextCheck);

        // If uninitialised or we have passed the next check time then get the state
        // to see if it has changed. If it has then update the value.
        nextCheckEpochMs.accumulateAndGet(checkIntervalMs, (nextCheckEpochMs, interval) -> {
            final long nowMs = System.currentTimeMillis();
            if (nowMs > nextCheckEpochMs) {
                final S newState = stateSupplier.get();
                if (nextCheckEpochMs == UNINITIALISED || !Objects.equals(state, newState)) {
                    state = newState;
                    final V newValue = valueSupplier.apply(newState);
                    LOGGER.debug("nextCheck: {}, state: {}, newState: {}, value: {}, newValue: {}",
                            oldNextCheck, state, newState, value, newValue);
                    value = newValue;
                }
                // Advance the next check time
                return nowMs + interval;
            } else {
                LOGGER.debug("No change");
                return nextCheckEpochMs;
            }
        });
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
}
