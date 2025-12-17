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

package stroom.pipeline.refdata.store;

import stroom.task.api.TaskTerminatedException;
import stroom.util.concurrent.UncheckedInterruptedException;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import com.google.common.util.concurrent.Striped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.locks.Lock;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public abstract class AbstractRefDataStore implements RefDataStore {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractRefDataStore.class);
    private static final LambdaLogger LAMBDA_LOGGER = LambdaLoggerFactory.getLogger(AbstractRefDataStore.class);

    @Override
    public RefDataValueProxy getValueProxy(final MapDefinition mapDefinition, final String key) {
        LOGGER.trace("getValueProxy([{}], [{}])", mapDefinition, key);
        return new SingleRefDataValueProxy(this, mapDefinition, key);
    }

    /**
     * Get an instance of a {@link RefDataLoader} for bulk loading multiple entries for a given
     * {@link RefStreamDefinition} and its associated effectiveTimeMs. The {@link RefDataLoader}
     * should be used in a try with resources block to ensure any transactions are closed, e.g.
     * <pre>try (RefDataLoader refDataLoader = refDataOffHeapStore.getLoader(...)) { ... }</pre>
     */
    protected abstract RefDataLoader createLoader(RefStreamDefinition refStreamDefinition,
                                                  long effectiveTimeMs);


    @Override
    public boolean doWithLoaderUnlessComplete(final RefStreamDefinition refStreamDefinition,
                                              final long effectiveTimeMs,
                                              final Consumer<RefDataLoader> work) {

        final boolean result;
        try (final RefDataLoader refDataLoader = createLoader(refStreamDefinition, effectiveTimeMs)) {
            // we now hold the lock for this RefStreamDefinition so re-test the completion state

            final Optional<ProcessingState> optLoadState = getLoadState(refStreamDefinition);

            final boolean isRefLoadRequired = optLoadState
                    .filter(loadState ->
                            loadState.equals(ProcessingState.COMPLETE))
                    .isEmpty();
            LOGGER.debug("optLoadState {}, isRefLoadRequired {}", optLoadState, isRefLoadRequired);

            if (optLoadState.isPresent() && optLoadState.get().equals(ProcessingState.FAILED)) {
                // A previous load of this ref stream failed so no point trying again.
                LOGGER.error("Existing reference data load is in a failed state. No point trying again. " +
                                "If the cause is environmental then purge the ref stream from the store and " +
                                "perform another lookup. {}",
                        refStreamDefinition.asUiFriendlyString());
                throw new RuntimeException(LogUtil.message(
                        "Reference Data is in a failed state from a previous load, aborting this load. {}",
                        refStreamDefinition.asUiFriendlyString()));
            } else if (optLoadState.isPresent() && optLoadState.get().equals(ProcessingState.COMPLETE)) {
                // If we get here then the data was not loaded when we checked before getting the lock,
                // so we waited for the lock and in the meantime another stream did the load.
                // Thus, we can drop out.
                LOGGER.info("Reference Data is already successfully loaded for {}, so doing nothing",
                        refStreamDefinition);
                result = false;
            } else {
                // Ref stream not in the store or a previous load was terminated part way through so
                // do the load (again)

                optLoadState.ifPresent(processingState -> {
                    if (ProcessingState.LOAD_IN_PROGRESS.equals(processingState)
                            || ProcessingState.PURGE_IN_PROGRESS.equals(processingState)) {
                        LOGGER.error("Unexpected processing state {}. This should not happen. " +
                                        "We hold the lock though, so will load anyway. {}",
                                processingState,
                                refStreamDefinition.asUiFriendlyString());
                    }
                });

                LOGGER.debug("Performing work with loader for {}", refStreamDefinition);
                work.accept(refDataLoader);

                result = true;
            }
        } catch (final TaskTerminatedException e) {
            LAMBDA_LOGGER.debug(() -> "Task terminated: " + e.getMessage());
            throw e;
        } catch (final UncheckedInterruptedException e) {
            LAMBDA_LOGGER.debug(() -> "Interrupted: " + e.getMessage());
            throw new TaskTerminatedException();
        } catch (final Exception e) {
            String msg = e.getMessage();
            // May get suppressed exceptions from the try-with-resources
            if (e.getSuppressed() != null && e.getSuppressed().length > 0) {
                msg += " " + Arrays.stream(e.getSuppressed())
                        .map(Throwable::getMessage)
                        .collect(Collectors.joining(" "));
            }
            throw new RuntimeException(LogUtil.message(
                    "Error using reference loader for {}: {}",
                    refStreamDefinition.asUiFriendlyString(), msg), e);
        }
        return result;
    }

    protected void doWithRefStreamDefinitionLock(final Striped<Lock> refStreamDefStripedReentrantLock,
                                                 final RefStreamDefinition refStreamDefinition,
                                                 final Runnable work) {

        final Lock lock = refStreamDefStripedReentrantLock.get(refStreamDefinition);

        LAMBDA_LOGGER.logDurationIfDebugEnabled(
                () -> {
                    try {
                        LOGGER.debug("Acquiring lock for {}", refStreamDefinition);
                        lock.lockInterruptibly();
                    } catch (final InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(LogUtil.message(
                                "Thread interrupted while trying to acquire lock for refStreamDefinition {}",
                                refStreamDefinition.asUiFriendlyString()));
                    }
                },
                () -> LogUtil.message("Acquiring lock for {}", refStreamDefinition));
        try {
            // now we have sole access to this RefStreamDefinition so perform the work on it
            work.run();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public String toString() {
        return getName();
    }
}
