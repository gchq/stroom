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

package stroom.index;

import stroom.index.shared.IndexShard;
import stroom.streamstore.fs.FileSystemUtil;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Not very OO but added here for GWT reasons.
 */
public class IndexShardUtil {
    public static Path getIndexPath(IndexShard indexShard) {
        StringBuilder builder = new StringBuilder();
        builder.append(indexShard.getVolume().getPath());
        builder.append(FileSystemUtil.SEPERATOR_CHAR);
        builder.append("index");
        builder.append(FileSystemUtil.SEPERATOR_CHAR);
        builder.append(indexShard.getIndex().getId());
        builder.append(FileSystemUtil.SEPERATOR_CHAR);
        builder.append(indexShard.getPartition());
        builder.append(FileSystemUtil.SEPERATOR_CHAR);
        builder.append(indexShard.getId());
        return Paths.get(builder.toString());
    }
}
