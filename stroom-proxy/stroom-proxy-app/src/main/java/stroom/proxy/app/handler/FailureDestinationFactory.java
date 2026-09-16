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

package stroom.proxy.app.handler;

import stroom.aws.s3.client.S3ClientPool;
import stroom.aws.s3.client.S3MetaKeysMapper;
import stroom.cache.api.TemplateCache;
import stroom.proxy.app.ProxyConfig;
import stroom.proxy.app.pipeline.config.PipelineMode;
import stroom.proxy.repo.store.FileStores;
import stroom.util.io.PathCreator;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Builds the destination a forward destination gives up to: a directory or an S3 bucket,
 * configured independently of where the destination forwards
 * ({@code designs/stages/forward.md} §4.3).
 */
@Singleton
public class FailureDestinationFactory {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(FailureDestinationFactory.class);

    static final String DEFAULT_FAILURE_DIR_NAME = "03_failure";

    private final S3ClientPool s3ClientPool;
    private final TemplateCache templateCache;
    private final S3MetaKeysMapper s3MetaKeysMapper;
    private final RemoteS3EventClient remoteS3EventClient;
    private final Provider<ProxyConfig> proxyConfigProvider;

    @Inject
    public FailureDestinationFactory(final S3ClientPool s3ClientPool,
                                     final TemplateCache templateCache,
                                     final S3MetaKeysMapper s3MetaKeysMapper,
                                     final RemoteS3EventClient remoteS3EventClient,
                                     final Provider<ProxyConfig> proxyConfigProvider) {
        this.s3ClientPool = s3ClientPool;
        this.templateCache = templateCache;
        this.s3MetaKeysMapper = s3MetaKeysMapper;
        this.remoteS3EventClient = remoteS3EventClient;
        this.proxyConfigProvider = proxyConfigProvider;
    }

    /**
     * @param destinationName The forward destination this give-up destination belongs to.
     * @param forwardingDir   That destination's own forwarding directory, which is where the default
     *                        failure directory lives.
     * @param config          Where give-up data goes, or null for the default directory.
     * @param fileStores      A file give-up directory is registered here so that monitoring sees it;
     *                        an S3 one has no local directory to register.
     * @param fileStoreOrder  The order to register the directory under.
     * @param pathCreator     Resolves a configured path and the sub-path template.
     */
    public Destination create(final String destinationName,
                              final Path forwardingDir,
                              final FailureDestinationConfig config,
                              final FileStores fileStores,
                              final int fileStoreOrder,
                              final PathCreator pathCreator) {
        Objects.requireNonNull(destinationName);
        Objects.requireNonNull(forwardingDir);

        final DestinationType type = config != null
                ? config.getType()
                : DestinationType.FILE;

        final Destination destination = switch (type) {
            case FILE -> createFileDestination(
                    destinationName, forwardingDir, config, fileStores, fileStoreOrder, pathCreator);
            case S3 -> createS3Destination(destinationName, config);
            // FailureDestinationConfig rejects these on construction, so this is unreachable unless
            // that guard is weakened, in which case failing here is better than picking one.
            case HTTP -> throw new IllegalStateException(LogUtil.message(
                    "'{}' cannot use a {} failure destination", destinationName, type));
        };

        LOGGER.info("'{}' - give-up data goes to {}", destinationName, destination);
        return destination;
    }

    private Destination createFileDestination(final String destinationName,
                                              final Path forwardingDir,
                                              final FailureDestinationConfig config,
                                              final FileStores fileStores,
                                              final int fileStoreOrder,
                                              final PathCreator pathCreator) {
        final Path failureDir = config != null && config.getPath() != null
                ? pathCreator.toAppPath(config.getPath())
                : forwardingDir.resolve(DEFAULT_FAILURE_DIR_NAME);

        final PathTemplateConfig subPathTemplate = config != null && config.getSubPathTemplate() != null
                ? config.getSubPathTemplate()
                : PathTemplateConfig.DEFAULT;

        DirUtil.ensureDirExists(failureDir);

        // The atomic move is safe to ask for whatever the filesystem: the destination falls back to a
        // copy beside the target when the rename cannot cross the boundary, so a failure directory on
        // shared storage works as well as one inside the proxy's own data directory. In shared mode
        // that directory is required to be shared, so every node gives up into it and each must
        // number under a writer root of its own.
        final FileDestination destination = new FileDestination(
                failureDir,
                destinationName + " (failures)",
                subPathTemplate,
                null,
                null,
                pathCreator,
                true,
                PipelineMode.isShared(proxyConfigProvider.get().getPipelineConfig()));

        fileStores.add(fileStoreOrder, "forward - " + destinationName + " - failure", failureDir);
        return destination;
    }

    private Destination createS3Destination(final String destinationName,
                                            final FailureDestinationConfig config) {
        // Synthesised rather than taken from config: enabled, instant, retry and threads belong to a
        // forward destination, and a give-up destination is none of those things.
        final ForwardS3Config s3Config = new ForwardS3Config(
                true,
                false,
                config.getNotificationType(),
                destinationName + " (failures)",
                config.getS3ClientConfig(),
                null,
                null,
                null,
                config.getAdditionalMetaKeysAllowSet());

        return new S3Destination(
                destinationName + " (failures)",
                s3Config,
                s3ClientPool,
                templateCache,
                s3MetaKeysMapper,
                remoteS3EventClient);
    }
}
