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

import stroom.proxy.app.ProxyConfig;
import stroom.proxy.app.pipeline.config.PipelineMode;
import stroom.util.io.PathCreator;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.nio.file.Path;

@Singleton
public class ForwardFileDestinationFactoryImpl implements ForwardFileDestinationFactory {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ForwardFileDestinationFactoryImpl.class);

    private final PathCreator pathCreator;
    private final Provider<ProxyConfig> proxyConfigProvider;

    @Inject
    public ForwardFileDestinationFactoryImpl(final PathCreator pathCreator,
                                             final Provider<ProxyConfig> proxyConfigProvider) {
        this.pathCreator = pathCreator;
        this.proxyConfigProvider = proxyConfigProvider;
    }

    @Override
    public Destination create(final ForwardFileConfig config) {
        final Path storeDir = pathCreator.toAppPath(config.getPath());
        DirUtil.ensureDirExists(storeDir);

        // In shared mode a file destination may be a mount every node delivers into, so each
        // process numbers under a writer root of its own (FileDestination).
        final FileDestination destination = new FileDestination(
                storeDir, config, pathCreator, PipelineMode.isShared(proxyConfigProvider.get().getPipelineConfig()));
        LOGGER.info("Created {} '{}' at {} with subPathTemplate '{}' (isInstant: {})",
                destination.getClass().getSimpleName(),
                config.getName(),
                destination.getWriterRoot(),
                config.getSubPathTemplate(),
                config.isInstant());
        return destination;
    }
}
