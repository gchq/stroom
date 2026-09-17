/*
 * Copyright 2024 Crown Copyright
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

package stroom.analytics.impl;

import stroom.util.concurrent.StripedLock;
import stroom.util.concurrent.UncheckedInterruptedException;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.Lock;
import java.util.function.Consumer;
import java.util.function.Function;

class DuplicateCheckStorePool<K, V> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DuplicateCheckStorePool.class);

    private final ConcurrentMap<K, References<V>> map = new ConcurrentHashMap<>();
    // Serialises the borrow/release protocol per key without holding a map bin lock while
    // opening or closing an env.
    private final StripedLock locks = new StripedLock();
    private final Function<K, V> objectFactory;
    private final Consumer<V> borrowHandler;
    private final Consumer<V> releaseHandler;
    private final Consumer<V> destructionHandler;

    public DuplicateCheckStorePool(final Function<K, V> objectFactory,
                                   final Consumer<V> borrowHandler,
                                   final Consumer<V> releaseHandler,
                                   final Consumer<V> destructionHandler) {
        this.objectFactory = objectFactory;
        this.borrowHandler = borrowHandler;
        this.releaseHandler = releaseHandler;
        this.destructionHandler = destructionHandler;
    }

    public <T> T use(final K key, final Function<V, T> consumer) {
        final V store = borrow(key);
        try {
            return consumer.apply(store);
        } finally {
            release(key);
        }
    }

    public V borrow(final K key) {
        return borrow(key, true);
    }

    private V borrow(final K key, final boolean createIfNotExists) {
        // Deliberately not ConcurrentHashMap.compute: creating the object opens an LMDB env
        // (and may delete a dir and block on a writer thread), and compute would run that
        // while synchronized on the key's bin, blocking every other operation on that bin.
        // Destruction closes an env and waits, uninterruptibly, for its writer to finish, so
        // it is worse again. A striped lock gives the same per key serialisation without
        // holding a lock the map itself needs. See gh-5689.
        final Lock lock = locks.getLockForKey(key);
        lock.lock();
        try {
            References<V> references = map.get(key);
            if (references == null) {
                if (!createIfNotExists) {
                    return null;
                }
                try {
                    references = new References<>(objectFactory.apply(key));
                } catch (final RuntimeException e) {
                    LOGGER.error(() -> LogUtil.message("Error creating object for key {}", key), e);
                    throw e;
                }
                map.put(key, references);
            }

            references.borrow();
            if (borrowHandler != null) {
                try {
                    borrowHandler.accept(references.object);
                } catch (final UncheckedInterruptedException e) {
                    LOGGER.debug(e::getMessage, e);
                } catch (final RuntimeException e) {
                    LOGGER.error(e::getMessage, e);
                }
            }
            return references.getObject();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Runs the action for the key while holding that key's lock, but only if nothing currently
     * holds the object. Lets a caller delete an object's backing files knowing that nothing is
     * using them and that nothing can borrow, and so open an env on them, while the action runs.
     *
     * @return true if the action ran, false if the object is in use and it did not.
     */
    public boolean doIfNotInUse(final K key, final Runnable action) {
        final Lock lock = locks.getLockForKey(key);
        lock.lock();
        try {
            if (map.containsKey(key)) {
                return false;
            }
            action.run();
            return true;
        } finally {
            lock.unlock();
        }
    }

    public void release(final K key) {
        // Same striped lock as borrow(), so the whole borrow/release protocol for a key is
        // serialised without holding a map bin lock across an env close.
        final Lock lock = locks.getLockForKey(key);
        lock.lock();
        try {
            final References<V> references = map.get(key);
            if (references == null) {
                throw new RuntimeException("Attempt to release object that doesn't exist");
            }

            final int count = references.release();
            if (releaseHandler != null) {
                try {
                    releaseHandler.accept(references.object);
                } catch (final UncheckedInterruptedException e) {
                    LOGGER.debug(e::getMessage, e);
                } catch (final RuntimeException e) {
                    LOGGER.error(e::getMessage, e);
                }
            }

            if (count == 0) {
                // Removed before destruction so nothing can borrow an object we are closing.
                // A borrow racing us waits on the lock above and then creates a fresh one.
                map.remove(key);
                if (destructionHandler != null) {
                    try {
                        destructionHandler.accept(references.object);
                    } catch (final UncheckedInterruptedException e) {
                        LOGGER.debug(e::getMessage, e);
                    } catch (final RuntimeException e) {
                        LOGGER.error(e::getMessage, e);
                    }
                }
            }
        } finally {
            lock.unlock();
        }
    }


    // --------------------------------------------------------------------------------


    private static class References<V> {

        private final V object;
        private volatile int referenceCount;

        public References(final V object) {
            this.object = object;
        }

        public synchronized void borrow() {
            referenceCount++;
        }

        public synchronized int release() {
            referenceCount--;
            if (referenceCount < 0) {
                throw new RuntimeException("referenceCount < 0");
            }
            return referenceCount;
        }

        private V getObject() {
            return object;
        }
    }
}
