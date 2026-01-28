package stroom.util.concurrent;

import java.util.function.Supplier;

/**
 * A thread-safe guard for managing resource lifecycle using reference counting.
 * <p>
 * Guards ensure that a resource cannot be destroyed while threads are actively using it,
 * preventing use-after-free errors in resources like native memory (LMDB), file handles,
 * or network connections.
 *
 * <h2>Lifecycle</h2>
 * <pre>
 * ACTIVE → acquire() succeeds, resource accessible
 *   ↓
 * DESTROYING → destroy() called, new acquire() calls throw TryAgainException
 *   ↓
 * DESTROYED → all acquisitions complete, clean-up callback runs
 * </pre>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * Guard guard = new StripedGuard(() -> db.close(), 8);
 *
 * // Use resource (thread-safe)
 * try {
 *     String value = guard.acquire(() -> db.get(key));
 * } catch (TryAgainException e) {
 *     // Resource destroyed, retry with new guard
 *     guard = getNewGuard();
 * }
 *
 * // Destroy when done
 * guard.destroy();
 * }</pre>
 *
 * <h2>Thread Safety</h2>
 * <ul>
 *   <li>Multiple threads can acquire concurrently without blocking</li>
 *   <li>Destroy can be called concurrently with acquisitions</li>
 *   <li>Clean-up callback runs exactly once after all acquisitions complete</li>
 * </ul>
 *
 * @see StripedGuard
 */
public interface Guard {

    /**
     * Acquires the guard, executes the supplier, and releases the guard.
     * <p>
     * The supplier executes while holding a reference to the resource, guaranteeing
     * the resource will not be destroyed during execution. Multiple threads can call
     * this concurrently without blocking each other.
     * <p>
     * If the supplier throws an exception, the guard is still properly released and
     * the exception propagates to the caller.
     *
     * @param <R>      return type of the supplier
     * @param supplier function to execute while holding the guard, must not be null
     * @return the result of calling {@code supplier.get()}
     * @throws TryAgainException    if destroy() has been called - caller should retry
     *                             with a new guard
     * @throws NullPointerException if supplier is null
     */
    <R> R acquire(Supplier<R> supplier);

    /**
     * Initiates destruction of the guard and its associated resource.
     * <p>
     * This method returns immediately. New acquisitions will throw {@link TryAgainException}.
     * The clean-up callback runs asynchronously when all active acquisitions complete.
     * <p>
     * This method is idempotent and thread-safe - it can be safely called multiple times
     * and concurrently with {@link #acquire(Supplier)}.
     *
     * <h3>Resource Rotation Example:</h3>
     * <pre>{@code
     * Guard oldGuard = currentGuard.get();
     * Guard newGuard = new StripedGuard(() -> newDb.close(), 8);
     *
     * currentGuard.set(newGuard);  // Switch atomically
     * oldGuard.destroy();           // Cleanup when safe
     * }</pre>
     */
    void destroy();

    /**
     * Exception thrown when attempting to acquire a destroyed guard.
     * <p>
     * This signals normal resource rotation, not an error. Callers should retry with
     * a fresh guard.
     *
     * <h3>Retry Pattern:</h3>
     * <pre>{@code
     * for (int i = 0; i < MAX_ATTEMPTS; i++) {
     *     try {
     *         return guard.acquire(() -> operation());
     *     } catch (TryAgainException e) {
     *         guard = getNewGuard();
     *     }
     * }
     * }</pre>
     */
    class TryAgainException extends RuntimeException {
        public TryAgainException() {
            super();
        }

        public TryAgainException(final String message) {
            super(message);
        }
    }
}
