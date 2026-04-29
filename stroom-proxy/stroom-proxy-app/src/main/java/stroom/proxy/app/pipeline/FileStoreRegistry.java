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

package stroom.proxy.app.pipeline;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
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
     * Create a registry containing every file store known to the supplied
     * factory.
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
     * Create a registry from the file stores already assembled by a pipeline
     * runtime.
     *
     * @param runtime The pipeline runtime.
     * @return A registry containing the runtime's instantiated stores.
     */
    public static FileStoreRegistry fromRuntime(final ProxyPipelineRuntime runtime) {
        Objects.requireNonNull(runtime, "runtime");
        return new FileStoreRegistry(runtime.getFileStores());
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
     * Resolve a queue message's file-store location to a local filesystem path.
     *
     * @param message The queue message containing the location to resolve.
     * @return The resolved local filesystem path.
     * @throws IOException If the message location cannot be resolved.
     */
    public Path resolve(final FileGroupQueueMessage message) throws IOException {
        Objects.requireNonNull(message, "message");
        return resolve(message.fileStoreLocation());
    }

    /**
     * Resolve a file-store location to a local filesystem path.
     *
     * @param location The file-store location to resolve.
     * @return The resolved local filesystem path.
     * @throws IOException If no matching store exists or the store rejects the
     * supplied location.
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

    /**
     * @return An immutable snapshot of registered file stores in name order.
     */
    public Map<String, FileStore> getFileStoresSortedByName() {
        return fileStores.entrySet()
                .stream()
                .sorted(Map.Entry.comparingByKey())
                .collect(LinkedHashMap::new,
                        (map, entry) -> map.put(entry.getKey(), entry.getValue()),
                        LinkedHashMap::putAll);
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
