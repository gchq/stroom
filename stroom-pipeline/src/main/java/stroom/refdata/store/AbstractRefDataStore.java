/*
 * Copyright 2018 Crown Copyright
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
 *
 */

package stroom.refdata.store;

import com.google.common.util.concurrent.Striped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.util.concurrent.locks.Lock;
import java.util.function.Consumer;

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
    protected abstract RefDataLoader loader(RefStreamDefinition refStreamDefinition,
                                            long effectiveTimeMs);


    public boolean doWithLoaderUnlessComplete(final RefStreamDefinition refStreamDefinition,
                                              final long effectiveTimeMs,
                                              final Consumer<RefDataLoader> work) {

        boolean result = false;
        try (RefDataLoader refDataLoader = loader(refStreamDefinition, effectiveTimeMs)) {
            // we now hold the lock for this RefStreamDefinition so test the completion state

            if (isDataLoaded(refStreamDefinition)) {
                LOGGER.debug("Data is already loaded for {}, so doing nothing", refStreamDefinition);
            } else {
                try {
                    work.accept(refDataLoader);
                } catch (Exception e) {
                    throw new RuntimeException(LambdaLogger.buildMessage(
                            "Error performing action with refDataLoader for {}", refStreamDefinition), e);
                }
                result = true;
            }
        } catch (Exception e) {
            throw new RuntimeException(LambdaLogger.buildMessage(
                    "Error closing refDataLoader for {}", refStreamDefinition), e);
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
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(LambdaLogger.buildMessage(
                                "Thread interrupted while trying to acquire lock for refStreamDefinition {}",
                                refStreamDefinition));
                    }
                },
                () -> LambdaLogger.buildMessage("Acquiring lock for {}", refStreamDefinition));
        try {
            // now we have sole access to this RefStreamDefinition so perform the work on it
            work.run();
        } finally {
            lock.unlock();
        }
    }
}
