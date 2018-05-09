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

package stroom.refdata.saxevents;

import stroom.properties.StroomPropertyService;
import stroom.util.ByteSizeUnit;
import stroom.util.logging.LambdaLogger;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

@Singleton
public class OffHeapInternPoolFactory {

    private static final String OFF_HEAP_PARENT_DIR_PROP_KEY = "stroom.offHeapData.parentDir";
    private static final String MAX_DB_SIZE_PROP_KEY = "stroom.offHeapData.internPool.maxDbSize";
    private static final String DIR_PREFIX = "OffHeapInternPool-";

    private final Map<String, OffHeapInternPool<?>> poolMap = new ConcurrentHashMap<>();
    private final Path offHeapParentDir;
    private final long maxDbSize;

    @Inject
    OffHeapInternPoolFactory(final StroomPropertyService stroomPropertyService) {

        offHeapParentDir = Optional.ofNullable(stroomPropertyService.getProperty(OFF_HEAP_PARENT_DIR_PROP_KEY))
                .map(dirStr -> Paths.get(dirStr))
                .orElseThrow(() -> new RuntimeException(LambdaLogger.buildMessage( "Property {} must have a value",
                        OFF_HEAP_PARENT_DIR_PROP_KEY)));

        maxDbSize = stroomPropertyService.getLongProperty(
                MAX_DB_SIZE_PROP_KEY,
                ByteSizeUnit.GIBIBYTE.longBytes(10));

        try {
            Files.createDirectories(offHeapParentDir);
        } catch (IOException e) {
            throw new RuntimeException(LambdaLogger.buildMessage("Unable to create directory {}",
                    offHeapParentDir.toAbsolutePath().toString()));
        }
    }


    public OffHeapInternPool<? extends AbstractPoolValue> getOffHeapInternPool(
            final String name,
            final Function<ByteBuffer, ? extends AbstractPoolValue> valueMapper) {

        final OffHeapInternPool<? extends AbstractPoolValue> pool = poolMap.computeIfAbsent(name, key -> {
            // TODO ensure name contains valid chars for a dir name, e.g. only [a-zA-Z]
            Path dbDir = offHeapParentDir.resolve(DIR_PREFIX + name);
            return new LmdbOffHeapInternPool<>(dbDir, maxDbSize, valueMapper);
        });

        return pool;
    }
}
