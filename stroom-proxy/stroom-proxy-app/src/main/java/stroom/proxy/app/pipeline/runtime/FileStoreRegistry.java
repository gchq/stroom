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

package stroom.proxy.app.pipeline.runtime;

import stroom.proxy.app.pipeline.queue.FileGroupQueueMessage;
import stroom.proxy.app.pipeline.store.FileGroupNotFoundException;
import stroom.proxy.app.pipeline.store.FileStore;
import stroom.proxy.app.pipeline.store.FileStoreLocation;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for named {@link FileStore} instances used when resolving queue
 * message file-group references.
 * <p>
 * Queue messages contain a {@link FileStoreLocation} rather than a direct
 * filesystem path. Consumers therefore need a small lookup layer that maps the
 * location's logical store name onto the runtime {@link FileStore} instance
 * capable of resolving that location.
 * </p>
 * <p>
 * This class deliberately keeps resolution centralised so stage processors do
 * not need to know how file stores are assembled or cached. A processor can
 * resolve either a complete {@link FileGroupQueueMessage} or the contained
 * {@link FileStoreLocation}.
 * </p>
 */
public class FileStoreRegistry {

    private final Map<String, FileStore> fileStores = new ConcurrentHashMap<>();

    public FileStoreRegistry() {
    }

    public FileStoreRegistry(final Collection<? extends FileStore> fileStores) {
        Objects.requireNonNull(fileStores, "fileStores")
                .forEach(this::register);
    }

    public FileStoreRegistry(final Map<String, ? extends FileStore> fileStores) {
        Objects.requireNonNull(fileStores, "fileStores")
                .forEach((name, fileStore) -> {
                    requireNonBlank(name, "fileStoreName");
                    register(fileStore);
                    if (!name.equals(fileStore.getName())) {
                        throw new IllegalArgumentException("File store map key '" + name
                                                           + "' does not match file store name '"
                                                           + fileStore.getName() + "'");
                    }
                });
    }

    /**
     * Create a registry holding <strong>every</strong> file store known to the supplied factory, not
     * only those whose stage is enabled in this process.
     * <p>
     * A queue message names the store <em>its producer</em> used, and in a distributed deployment
     * the producer is another node running a different stage (R1a). A registry restricted to this
     * node's enabled stages could not resolve those messages at all, so it holds every store even
     * though a single-stage node writes to few of them.
     * </p>
     *
     * @param fileStoreFactory The configured file-store factory.
     * @return A registry containing all configured stores.
     */
    public static FileStoreRegistry fromFactory(final FileStoreFactory fileStoreFactory) {
        Objects.requireNonNull(fileStoreFactory, "fileStoreFactory");

        final FileStoreRegistry registry = new FileStoreRegistry();
        fileStoreFactory.getFileStoreDefinitions()
                .keySet()
                .forEach(fileStoreName ->
                        registry.register(fileStoreFactory.getFileStore(fileStoreName)));
        return registry;
    }

    /**
     * Register a file store by its logical name.
     *
     * @param fileStore The file store to register.
     * @return This registry.
     */
    public FileStoreRegistry register(final FileStore fileStore) {
        Objects.requireNonNull(fileStore, "fileStore");
        final String fileStoreName = requireNonBlank(fileStore.getName(), "fileStore.name");
        fileStores.put(fileStoreName, fileStore);
        return this;
    }

    /**
     * @param fileStoreName The logical file-store name.
     * @return True if a store with the supplied name is registered.
     */
    public boolean hasFileStore(final String fileStoreName) {
        return fileStoreName != null && fileStores.containsKey(fileStoreName);
    }

    /**
     * Get a registered file store.
     *
     * @param fileStoreName The logical file-store name.
     * @return The file store, if present.
     */
    public Optional<FileStore> getFileStore(final String fileStoreName) {
        return Optional.ofNullable(fileStores.get(fileStoreName));
    }

    /**
     * Get a registered file store, throwing if it is not present.
     *
     * @param fileStoreName The logical file-store name.
     * @return The file store.
     * @throws IOException If no store is registered for the supplied name.
     */
    public FileStore requireFileStore(final String fileStoreName) throws IOException {
        final String nonBlankFileStoreName = requireNonBlank(fileStoreName, "fileStoreName");
        final FileStore fileStore = fileStores.get(nonBlankFileStoreName);

        if (fileStore == null) {
            throw new IOException("No file store is registered for logical file store '"
                                  + nonBlankFileStoreName + "'");
        }

        return fileStore;
    }

    /**
     * Resolve a queue message's file-store location to a path holding the complete group, which
     * then belongs to the caller ({@link FileStore#resolve}).
     *
     * @param message The queue message containing the location to resolve.
     * @return The resolved path.
     * @throws FileGroupNotFoundException If the store holds no complete group there - normally
     *                                    because the work was already done (R12).
     * @throws IOException                If no store is registered for the location's name, or the
     *                                    store rejects or cannot read the location.
     */
    public Path resolve(final FileGroupQueueMessage message) throws IOException {
        Objects.requireNonNull(message, "message");
        return resolve(message.fileStoreLocation());
    }

    /**
     * Resolve a file-store location to a path holding the complete group, which then belongs to the
     * caller ({@link FileStore#resolve}).
     *
     * @param location The file-store location to resolve.
     * @return The resolved path.
     * @throws FileGroupNotFoundException If the store holds no complete group there.
     * @throws IOException                If no store is registered for the location's name, or the
     *                                    store rejects or cannot read the location.
     */
    public Path resolve(final FileStoreLocation location) throws IOException {
        Objects.requireNonNull(location, "location");
        return requireFileStore(location.storeName()).resolve(location);
    }

    /**
     * @return An immutable snapshot of the registered file stores keyed by
     * logical store name.
     */
    public Map<String, FileStore> getFileStores() {
        return Map.copyOf(fileStores);
    }

    public int size() {
        return fileStores.size();
    }

    public boolean isEmpty() {
        return fileStores.isEmpty();
    }

    private static String requireNonBlank(final String value,
                                          final String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
