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

import stroom.proxy.app.pipeline.config.ProxyPipelineConfig;
import stroom.proxy.app.pipeline.store.FileStore;
import stroom.proxy.app.pipeline.store.FileStoreDefinition;
import stroom.proxy.app.pipeline.store.filesystem.FilesystemFileStore;
import stroom.proxy.app.pipeline.store.s3.S3FileStore;
import stroom.util.io.PathCreator;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.time.StroomDuration;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Builds the named {@link FileStore} instances a pipeline configuration defines, and caches them by
 * name so every stage that names a store gets the same instance.
 */
public class FileStoreFactory {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(FileStoreFactory.class);

    private static final String DEFAULT_FILE_STORE_ROOT = "data/pipeline/file-stores";

    private final Map<String, FileStoreDefinition> fileStoreDefinitions;
    private final PathCreator pathCreator;
    private final Map<String, FileStore> fileStoreCache = new ConcurrentHashMap<>();

    public FileStoreFactory(final ProxyPipelineConfig pipelineConfig,
                            final PathCreator pathCreator) {
        this(Objects.requireNonNull(pipelineConfig, "pipelineConfig").getFileStores(), pathCreator);
    }

    public FileStoreFactory(final Map<String, FileStoreDefinition> fileStoreDefinitions,
                            final PathCreator pathCreator) {
        this.fileStoreDefinitions = Map.copyOf(Objects.requireNonNull(fileStoreDefinitions, "fileStoreDefinitions"));
        this.pathCreator = Objects.requireNonNull(pathCreator, "pathCreator");
    }

    public FileStore getFileStore(final String fileStoreName) {
        final String nonBlankFileStoreName = requireNonBlank(fileStoreName, "fileStoreName");
        if (!fileStoreDefinitions.containsKey(nonBlankFileStoreName)) {
            throw new IllegalArgumentException("No file store definition exists for logical file store "
                                               + nonBlankFileStoreName);
        }
        return fileStoreCache.computeIfAbsent(nonBlankFileStoreName, this::createFileStore);
    }

    public Map<String, FileStoreDefinition> getFileStoreDefinitions() {
        return fileStoreDefinitions;
    }

    private FileStore createFileStore(final String fileStoreName) {
        final FileStoreDefinition definition = fileStoreDefinitions.get(fileStoreName);
        return switch (definition.getType()) {
            case LOCAL_FILESYSTEM, SHARED_FILESYSTEM -> new FilesystemFileStore(
                    fileStoreName,
                    getFilesystemPath(fileStoreName, definition),
                    definition.getType(),
                    definition.getEffectiveDurability(),
                    NullSafe.get(definition.getOrphanAge(), StroomDuration::getDuration));
            case S3 -> new S3FileStore(
                    fileStoreName,
                    definition,
                    getS3LocalRoot(fileStoreName, definition));
        };
    }

    private Path getS3LocalRoot(final String fileStoreName, final FileStoreDefinition definition) {
        final String path = definition.getLocalCachePath() == null
                ? DEFAULT_FILE_STORE_ROOT + "/s3-" + fileStoreName
                : definition.getLocalCachePath();
        return pathCreator.toAppPath(path);
    }

    private Path getFilesystemPath(final String fileStoreName, final FileStoreDefinition definition) {
        final String path = definition.getPath() == null
                ? DEFAULT_FILE_STORE_ROOT + "/" + fileStoreName
                : definition.getPath();
        return pathCreator.toAppPath(path);
    }

    private static String requireNonBlank(final String value, final String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }

    /**
     * Close everything built so far, for a caller abandoning a partial assembly. Must not throw: it
     * runs on a failure path, where a throw would replace the exception explaining why assembly was
     * abandoned with one about tidying up after it.
     */
    public void closeBuilt() {
        fileStoreCache.values().forEach(item -> {
            try {
                item.close();
            } catch (final Exception e) {
                LOGGER.error(() -> LogUtil.message(
                        "Unable to close {} while abandoning a partial pipeline assembly: {}",
                        item, LogUtil.exceptionMessage(e)), e);
            }
        });
        fileStoreCache.clear();
    }
}
