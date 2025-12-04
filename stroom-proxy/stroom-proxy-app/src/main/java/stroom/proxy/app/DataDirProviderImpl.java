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

package stroom.proxy.app;

import stroom.util.io.FileUtil;
import stroom.util.io.PathCreator;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import com.google.common.base.Strings;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Singleton
public class DataDirProviderImpl implements DataDirProvider {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DataDirProviderImpl.class);

    private final Path dataDir;

    @Inject
    DataDirProviderImpl(final ProxyPathConfig proxyPathConfig,
                        final PathCreator pathCreator) {
        if (Strings.isNullOrEmpty(proxyPathConfig.getData())) {
            throw new RuntimeException("No data directory have been provided in 'dataDir'");
        }

        this.dataDir = pathCreator.toAppPath(proxyPathConfig.getData());

        try {
            Files.createDirectories(dataDir);
        } catch (final IOException e) {
            LOGGER.error(LogUtil.message(
                    "Failed to create proxy data directory '{}'. This is configured using " +
                            "property {}. {}",
                    FileUtil.getCanonicalPath(dataDir),
                    proxyPathConfig.getFullPathStr(ProxyPathConfig.PROP_NAME_DATA),
                    e.getMessage()));
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public Path get() {
        return dataDir;
    }
}
