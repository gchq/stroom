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

package stroom.index.impl;

import stroom.index.shared.IndexShard;
import stroom.util.io.PathCreator;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Not very OO but added here for GWT reasons.
 */
public class IndexShardUtil {

    public static Path getIndexPath(final IndexShard indexShard,
                                    final PathCreator pathCreator) {
        try {
            Path path = pathCreator.toAppPath(indexShard.getVolume().getPath());

            if (!Files.isDirectory(path)) {
                throw new IOException("Volume path not found: " + indexShard.getVolume().getPath());
            }

            path = path.resolve("index");
            path = path.resolve(indexShard.getIndexUuid());
            path = path.resolve(indexShard.getPartition());
            path = path.resolve(String.valueOf(indexShard.getId()));

            return path;
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
