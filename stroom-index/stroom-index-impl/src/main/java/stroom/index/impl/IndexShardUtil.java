/*
 * Copyright 2016 Crown Copyright
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Not very OO but added here for GWT reasons.
 */
public class IndexShardUtil {

    public static Path getIndexPath(final IndexShard indexShard) {
        Path path = Paths.get(indexShard.getVolume().getPath());

        if (!Files.isDirectory(path)) {
            throw new RuntimeException("Volume path not found: " + indexShard.getVolume().getPath());
        }

        if (indexShard.getOldIndexId() != null) {
            // If we have a legacy shard then see if we can create a path for it.
            Path legacyPath = path.resolve("index");
            legacyPath = legacyPath.resolve(String.valueOf(indexShard.getOldIndexId()));
            legacyPath = legacyPath.resolve(indexShard.getPartition());
            legacyPath = legacyPath.resolve(String.valueOf(indexShard.getId()));
            if (Files.isDirectory(legacyPath)) {
                return legacyPath;
            }
        }

        path = path.resolve("index");
        path = path.resolve(indexShard.getIndexUuid());
        path = path.resolve(indexShard.getPartition());
        path = path.resolve(String.valueOf(indexShard.getId()));

        return path;
    }
}
