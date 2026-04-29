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

import stroom.util.io.PathCreator;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory for named {@link FileStore} instances used by the reference-message
 * proxy pipeline.
 * <p>
 * Pipeline stages reference file stores by logical name in
 * {@link PipelineStageConfig}. This factory resolves those logical names using
 * the {@link ProxyPipelineConfig#getFileStores()} map and creates the concrete
 * {@link FileStore} implementation for the definition.
 * </p>
 * <p>
 * The initial implementation supports local/shared filesystem stores only via
 * {@link LocalFileStore}. This matches the current {@link FileStoreLocation}
 * support and keeps queue messages as stable references to already-written data.
 * </p>
 */
public class FileStoreFactory {

    private static final String DEFAULT_FILE_STORE_ROOT = "data/pipeline/file-stores";

    private final Map<String, FileStoreDefinition> fileStoreDefinitions;
    private final PathCreator pathCreator;
    private final Map<String, FileStore> fileStoreCache = new ConcurrentHashMap<>();

    public FileStoreFactory(final ProxyPipelineConfig pipelineConfig,
                            final PathCreator pathCreator) {
        this(
                Objects.requireNonNull(pipelineConfig, "pipelineConfig").getFileStores(),
                pathCreator);
    }

    public FileStoreFactory(final Map<String, FileStoreDefinition> fileStoreDefinitions,
                            final PathCreator pathCreator) {
        this.fileStoreDefinitions = Map.copyOf(Objects.requireNonNull(
                fileStoreDefinitions,
                "fileStoreDefinitions"));
        this.pathCreator = Objects.requireNonNull(pathCreator, "pathCreator");
    }

    /**
     * Get or create a named file store.
     * <p>
     * Instances are cached by logical store name so repeated calls for the same
     * store return the same {@link FileStore} instance for this factory.
     * </p>
     *
     * @param fileStoreName The logical file-store name.
     * @return The file store.
     */
    public FileStore getFileStore(final String fileStoreName) {
        final String nonBlankFileStoreName = requireNonBlank(fileStoreName, "fileStoreName");

        if (!fileStoreDefinitions.containsKey(nonBlankFileStoreName)) {
            throw new IllegalArgumentException("No file store definition exists for logical file store "
                                               + nonBlankFileStoreName);
        }

        return fileStoreCache.computeIfAbsent(nonBlankFileStoreName, this::createFileStore);
    }

    public boolean hasFileStore(final String fileStoreName) {
        return fileStoreDefinitions.containsKey(fileStoreName);
    }

    public Map<String, FileStoreDefinition> getFileStoreDefinitions() {
        return fileStoreDefinitions;
    }

    private FileStore createFileStore(final String fileStoreName) {
        final FileStoreDefinition definition = fileStoreDefinitions.get(fileStoreName);

        if (definition == null) {
            throw new IllegalArgumentException("No file store definition exists for logical file store "
                                               + fileStoreName);
        }

        return new LocalFileStore(
                fileStoreName,
                getLocalFilesystemFileStorePath(fileStoreName, definition));
    }

    private Path getLocalFilesystemFileStorePath(final String fileStoreName,
                                                 final FileStoreDefinition definition) {
        final String configuredPath = definition.getPath();
        final String path = configuredPath == null
                ? DEFAULT_FILE_STORE_ROOT + "/" + fileStoreName
                : configuredPath;
        return pathCreator.toAppPath(path);
    }

    private static String requireNonBlank(final String value,
                                          final String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
